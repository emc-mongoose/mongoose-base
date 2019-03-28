# Environment Requirements

* Java 11+ or Docker
* OS open files limit is at least a bit higher than specified concurrency level
* Few gigabytes of free memory.

High-load tests may allocate up to 1-2 GB of the memory depending on the scenario.
* (Remote Storage) Connectivity with the endpoint nodes via the ports used
* (Distributed Mode) Connectivity with the additional/remote nodes via port #1099 (RMI)
* (Remote Monitoring) Connectivity with the nodes via port #9010 (JMX)

# Jar

Mongoose is distributed as a single jar file from:
http://central.maven.org/maven2/com/github/emc-mongoose/mongoose-base/

# Docker

Mongoose images are stored in the [Docker Hub](https://hub.docker.com/u/emcmongoose/)

## Base

**Note**
> The base image doesn't contain any additonal load step types neither additional storage drivers. Please use one of the
> specific images either consider using the [backward compatibility bundle](https://github.com/emc-mongoose/mongoose)

### Standalone

The base image may be used in the standalone mode:
```bash
docker run \
    --network host \
    emcmongoose/mongoose[-<TYPE>] [\
    <ARGS>]
```

### Distributed Mode

#### Node

First, it's necessary to start some node/peer services:
```bash
docker run \
    --network host \
    emcmongoose/mongoose[-<TYPE>] \
    --run-node [\
    --load-step-node-port=<PORT>]
```

#### Run

To invoke the run in the distributed mode it's necessary to specify the additional node/peer addresses:
```bash
docker run \
    --network host \
    emcmongoose/mongoose[-<TYPE>] \
    --load-step-node-addrs=<ADDR1,ADDR2,...> [\
    <ARGS>]
```

## Additional Notes

### Logs Sharing

The example below mounts the host's directory `./log` to the container's
`/root/.mongoose/<VERSION>/log` (where mongoose holds its log files).

```bash
docker run \
    --network host \
    --mount type=bind,source="$(pwd)"/log,target=/root/.mongoose/<VERSION>/log
    emcmongoose/<IMAGE> \
    [<ARGS>]
```

### Debugging

The example below starts the Mongoose in the container with remote
debugging capability via the port #5005.

```bash
docker run \
    --network host \
    --expose 5005
    --entrypoint /opt/mongoose/entrypoint-debug.sh \
    emcmongoose/<IMAGE> \
    [<ARGS>]
```
