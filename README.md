# File Reader User Manual 1.0.0

## Document Information

### Introduction

The File reader reads the entire file and sends it as one raw message. File reader produces **raw messages**. See **RawMessage** type in infra.proto.

### Quick start

General view of the component will look like this:

```yaml
apiVersion: th2.exactpro.com/v1
kind: Th2Box
metadata:
  name: read-file
spec:
  image-name: ghcr.io/th2-net/th2-read-file
  image-version: <image version>
  type: th2-read
  custom-config:
    filesDirectory: "files/dir"
      aliases:
        A:
          pathFilter: "fileA.*"
        B:
          pathFilter: "fileB.*"
        C:
          pathFilter: "fileC.*"
      common:
        staleTimeout: "PT1S"
        maxBatchSize: 100
        maxPublicationDelay: "PT5S"
        leaveLastFileOpen: false
        fixTimestamp: false
        maxBatchesPerSecond: -1 # unlimited
      pullingInterval: "PT5S"
  pins:
    - name: read_file_out
      connection-type: mq
      attributes: [ 'raw', 'publish', 'store' ]
  extended-settings:
    service:
      enabled: false
    envVariables:
      JAVA_TOOL_OPTIONS: "-XX:+ExitOnOutOfMemoryError"
    mounting:
      - path: "<destination path in Kubernetes pod>"
        pvcName: <Kubernetes persistent volume component name >
    resources:
      # Min system requirments ...
      limits:
        memory: 200Mi
        cpu: 200m
      requests:
        memory: 100Mi
        cpu: 50m
```

#### Configuration

##### Reader configuration

+ filesDirectory - the directory to watch files
+ aliases - the mapping between alias and files that correspond to that alias
    + pathFilter - filter for files that correspond to that alias
+ common - the common configuration for read core. Please found the description [here](https://github.com/th2-net/th2-read-file-common-core/blob/master/README.md#configuration). NOTE: the fields with `Duration` type should be described in
  the following format `PT<number><time unit>`. Supported time units (**H** - hours,**M** - minutes,**S** - seconds). E.g. PT5S - 5 seconds, PT5M - 5 minutes, PT0.001S - 1 millisecond
+ pullingInterval - how often the directory will be checked for updates after receiving updates

##### Pin declaration

The File reader requires a single pin with _publish_ and _raw_ attributes. The data is published in a raw format. To use it please conect the output pin with another pin that transforms raw data to parsed data. E.g. the **codec** box.

Example:

```yaml
apiVersion: th2.exactpro.com/v1
kind: Th2Box
metadata:
  name: file-reader
spec:
  pins:
    - name: out_file
      connection-type: mq
      attributes: [ 'raw', 'publish', 'store' ]
```

## Changes

### 1.0.0

+ Create a read-file component
