/*
 * Copyright 2020-2023 Exactpro (Exactpro Systems Limited)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.exactpro.th2.readfile;

import static java.util.Comparator.comparing;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.Direction;
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.GroupBatch;
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.MessageGroup;
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.MessageId;
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.RawMessage;
import com.exactpro.th2.read.file.common.impl.TransportDefaultFileReader;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.exactpro.th2.common.event.Event;
import com.exactpro.th2.common.event.EventUtils;
import com.exactpro.th2.common.grpc.EventBatch;
import com.exactpro.th2.common.grpc.EventID;
import com.exactpro.th2.common.metrics.CommonMetrics;
import com.exactpro.th2.common.schema.factory.CommonFactory;
import com.exactpro.th2.common.schema.message.MessageRouter;
import com.exactpro.th2.read.file.common.DirectoryChecker;
import com.exactpro.th2.read.file.common.FileSourceWrapper;
import com.exactpro.th2.read.file.common.MovedFileTracker;
import com.exactpro.th2.read.file.common.StreamId;
import com.exactpro.th2.read.file.common.state.impl.InMemoryReaderState;
import com.exactpro.th2.readfile.cfg.FileReaderConfiguration;
import com.exactpro.th2.readfile.impl.FileBytesReader;
import com.exactpro.th2.readfile.impl.FileParser;
import com.exactpro.th2.readfile.impl.FileWrapper;

import kotlin.Unit;

public class Main {

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        Deque<AutoCloseable> toDispose = new ArrayDeque<>();
        var lock = new ReentrantLock();
        var condition = lock.newCondition();
        configureShutdownHook(toDispose, lock, condition);

        CommonMetrics.LIVENESS_MONITOR.enable();
        CommonFactory commonFactory = CommonFactory.createFromArguments(args);
        toDispose.add(commonFactory);

        MessageRouter<GroupBatch> messageRouter = commonFactory.getTransportGroupBatchRouter();
        MessageRouter<EventBatch> eventBatchRouter = commonFactory.getEventBatchRouter();

        FileReaderConfiguration configuration = commonFactory.getCustomConfiguration(FileReaderConfiguration.class);
        Comparator<Path> pathComparator = comparing(it -> it.getFileName().toString(), String.CASE_INSENSITIVE_ORDER);
        var directoryChecker = new DirectoryChecker(
                configuration.getFilesDirectory(),
                (Path path) -> configuration.getAliases().entrySet().stream()
                        .filter(entry -> entry.getValue().getPathFilter().matcher(path.getFileName().toString()).matches())
                        .map(it -> new StreamId(it.getKey()))
                        .collect(Collectors.toSet()),
                files -> files.sort(pathComparator),
                path -> true
        );

        if (configuration.getPullingInterval().isNegative()) {
            throw new IllegalArgumentException("Pulling interval " + configuration.getPullingInterval() + " must not be negative");
        }

        String book = commonFactory.getBoxConfiguration().getBookName();
        Supplier<GroupBatch.Builder> batchSupplier = () -> GroupBatch.builder().setBook(book);

        try {
            EventID rootId = commonFactory.getRootEventId();

            var reader = new TransportDefaultFileReader.Builder<>(
                    configuration.getCommon(),
                    directoryChecker,
                    new FileParser(),
                    new MovedFileTracker(configuration.getFilesDirectory()),
                    new InMemoryReaderState(),
                    streamId -> MessageId.builder().setSessionAlias(streamId.getSessionAlias())
                            .setDirection(Direction.INCOMING),
                    Main::createSource
            )
                    .readFileAfterStaleTimeout()
                    .acceptNewerFiles()
                    .onStreamData((streamId, builders) ->
                            publishMessages(messageRouter, streamId, builders, batchSupplier))
                    .onError((streamId, message, ex) ->
                            publishErrorEvent(eventBatchRouter, streamId, message, ex, rootId))
                    .onSourceCorrupted((streamId, path, ex) ->
                            publishSourceCorruptedEvent(eventBatchRouter, path, streamId, ex, rootId))
                    .build();

            toDispose.add(reader);

            ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
            toDispose.add(() -> {
                executorService.shutdown();
                if (executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    LOGGER.warn("Cannot shutdown executor for 5 seconds");
                    executorService.shutdownNow();
                }
            });

            ScheduledFuture<?> future = executorService.scheduleWithFixedDelay(reader::processUpdates, 0, configuration.getPullingInterval().toMillis(), TimeUnit.MILLISECONDS);
            CommonMetrics.READINESS_MONITOR.enable();
            awaitShutdown(lock, condition);
            future.cancel(true);
        } catch (InterruptedException e) {
            LOGGER.error("Cannot read files from: {}", configuration.getFilesDirectory(), e);
        }
    }

    @NotNull
    private static Unit publishSourceCorruptedEvent(MessageRouter<EventBatch> eventBatchRouter, Path path, StreamId streamId, Exception e, EventID rootEventId) {
        Event error = Event.start()
                .name("Corrupted source " + path + " for " + streamId.getSessionAlias())
                .type("CorruptedSource");
        return publishError(eventBatchRouter, streamId, e, error, rootEventId);
    }

    @NotNull
    private static Unit publishErrorEvent(MessageRouter<EventBatch> eventBatchRouter, StreamId streamId, String message, Exception ex, EventID rootEventId) {
        Event error = Event.start().endTimestamp()
                .name(streamId == null ? "General error" : "Error for session alias " + streamId.getSessionAlias())
                .type("Error")
                .bodyData(EventUtils.createMessageBean(message));
        return publishError(eventBatchRouter, streamId, ex, error, rootEventId);
    }

    @NotNull
    private static Unit publishError(MessageRouter<EventBatch> eventBatchRouter, StreamId streamId, Exception ex, Event error, EventID rootEventId) {
        Throwable tmp = ex;
        while (tmp != null) {
            error.bodyData(EventUtils.createMessageBean(tmp.getMessage()));
            tmp = tmp.getCause();
        }
        try {
            eventBatchRouter.sendAll(EventBatch.newBuilder().addEvents(error.toProto(rootEventId)).build());
        } catch (Exception e) {
            LOGGER.error("Cannot send event for stream {}", streamId, e);
        }
        return Unit.INSTANCE;
    }

    @NotNull
    private static Unit publishMessages(
            MessageRouter<GroupBatch> rawMessageBatchRouter,
            StreamId streamId,
            List<? extends RawMessage.Builder> builders,
            Supplier<GroupBatch.Builder> batchSupplier
    ) {
        try {
            var batch = batchSupplier.get()
                    .setSessionGroup(streamId.getSessionAlias());
            for (var msg : builders) {
                batch.addGroup(
                        new MessageGroup(List.of(msg.build()))
                );
            }
            rawMessageBatchRouter.sendAll(batch.build());
        } catch (Exception e) {
            LOGGER.error("Cannot publish batch for {}", streamId, e);
        }
        return Unit.INSTANCE;
    }

    private static FileSourceWrapper<FileWrapper> createSource(StreamId streamId, Path path) {
        return new FileBytesReader<>(new FileWrapper(path));
    }

    private static void configureShutdownHook(Deque<AutoCloseable> resources, ReentrantLock lock, Condition condition) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.info("Shutdown start");
            CommonMetrics.READINESS_MONITOR.disable();
            try {
                lock.lock();
                condition.signalAll();
            } finally {
                lock.unlock();
            }
            resources.descendingIterator().forEachRemaining(resource -> {
                try {
                    resource.close();
                } catch (Exception e) {
                    LOGGER.error("Cannot close resource {}", resource.getClass(), e);
                }
            });

            CommonMetrics.LIVENESS_MONITOR.disable();
            LOGGER.info("Shutdown end");
        }, "Shutdown hook"));
    }

    private static void awaitShutdown(ReentrantLock lock, Condition condition) throws InterruptedException {
        try {
            lock.lock();
            LOGGER.info("Wait shutdown");
            condition.await();
            LOGGER.info("App shutdown");
        } finally {
            lock.unlock();
        }
    }

}