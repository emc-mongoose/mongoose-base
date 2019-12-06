[![Gitter chat](https://badges.gitter.im/emc-mongoose.png)](https://gitter.im/emc-mongoose)
[![Issue Tracker](https://img.shields.io/badge/Issue-Tracker-red.svg)](https://mongoose-issues.atlassian.net/projects/GOOSE)
[![CI status](https://gitlab.com/emc-mongoose/mongoose-base/badges/master/pipeline.svg)](https://gitlab.com/emc-mongoose/mongoose-base/commits/master)
[![Tag](https://img.shields.io/github/tag/emc-mongoose/mongoose-base.svg)](https://github.com/emc-mongoose/mongoose-base/tags)
[![Maven metadata URL](https://img.shields.io/maven-metadata/v/http/central.maven.org/maven2/com/github/emc-mongoose/mongoose-base/maven-metadata.xml.svg)](http://central.maven.org/maven2/com/github/emc-mongoose/mongoose-base)
[![Sonatype Nexus (Releases)](https://img.shields.io/nexus/r/http/oss.sonatype.org/com.github.emc-mongoose/mongoose-base.svg)](http://oss.sonatype.org/com.github.emc-mongoose/mongoose-base)
[![Docker Pulls](https://img.shields.io/docker/pulls/emcmongoose/mongoose-base.svg)](https://hub.docker.com/r/emcmongoose/mongoose-base/)

# Contents

1. [Overview](#1-overview)
2. [Features](#2-features)<br/>
&nbsp;&nbsp;2.1. [Scalability](#21-scalability)<br/>
&nbsp;&nbsp;&nbsp;&nbsp;2.1.1. [Vertical](#211-vertical)<br/>
&nbsp;&nbsp;&nbsp;&nbsp;2.1.2. [Horizontal](#212-horizontal)<br/>
&nbsp;&nbsp;2.2. [Customization](#22-customization)<br/>
&nbsp;&nbsp;&nbsp;&nbsp;2.2.1. [Flexible Configuration](#221-flexible-configuration)<br/>
&nbsp;&nbsp;&nbsp;&nbsp;2.2.2. [Load Generation Patterns](#222-load-generation-patterns)<br/>
&nbsp;&nbsp;&nbsp;&nbsp;2.2.3. [Scenarios](#223-scenarios)<br/>
&nbsp;&nbsp;&nbsp;&nbsp;2.2.4. [Metrics Reporting](#224-metrics-reporting)<br/>
&nbsp;&nbsp;2.3. [Extension](#23-extension)<br/>
&nbsp;&nbsp;&nbsp;&nbsp;2.3.1. [Load Steps](#231-load-steps)<br/>
&nbsp;&nbsp;&nbsp;&nbsp;2.3.2. [Storage Drivers](#232-storage-drivers)<br/>
&nbsp;&nbsp;&nbsp;&nbsp;2.3.3. [Scenario Engine](#233-scenario-engine)<br/>
3. [Get started](doc/GETSTARTED.md)<br/>
4. [Comparison With Similar Tools](doc/COMPARISION.md)<br/>
5. [Documentation](doc/)<br/>
6. [Contributing](doc/CONTRIBUTING.md)<br/>
7. [Changelog](doc/changelog)<br/>

# 1. Overview

Mongoose is a distributed storage performance testing tool. This repo contains the basic functionality only. See the 
[extensions](#23-extension) for the actual use. 

# 2. Features

## 2.1. Scalability

### 2.1.1. Vertical

Using [fibers](https://github.com/akurilov/fiber4j) allows to sustain millions of concurrent operations easily without
significant performance degradation.

### 2.1.2. Horizontal

The [distributed mode](doc/design/distributed_mode) in Mongoose was designed as P2P network. Each peer/node performs
independently as much as possible. This eliminates the excess network interaction between the nodes which may be a
bottleneck.

## 2.2. Customization

### 2.2.1. Flexible Configuration

* Safe: the configuration options are being checked against the schema
* Extensible: Mongoose's plugins may come up with own configuration options making them available from the joint CLI and being checked against the schema 
* [Expressions](doc/usage/input/configuration#124-expression) allow to specify the dynamically changing values 

### 2.2.2. Load Generation Patterns

* CRUD operations and the extensions: Noop, [Copy](doc/design/copy_mode), etc

* [Parial Operations](doc/usage/load/operations/byte_ranges)

* [Composite Operations](doc/usage/load/operations/composite)

* Complex Load Steps
    * [Pipeline Load](https://github.com/emc-mongoose/mongoose-load-step-pipeline)
    * [Weighted Load](https://github.com/emc-mongoose/mongoose-load-step-weighted)
* [Recycle Mode](doc/design/recycle_mode)

* [Data Reentrancy](doc/design/data_reentrancy)

  Allows to validate the data read back from the storage successfully even after the data items have been randomly
  updated multiple times before

* Custom Payload Data

### 2.2.3. [Scenarios](doc/usage/input/scenarios)

Scenaruis allow to organize the load steps in the required order and reuse the complex performance tests

### 2.2.4. [Metrics Reporting](doc/usage/output#2-metrics)

The metrics reported by Mongoose are designed to be most useful for the performance analysis. The following metrics are
available:

* Counts

  * Items
  * Bytes transferred
  * Time
    * Effective
    * Elapsed

* Rates

  * Items per second
  * Bytes per second

* Timing distributions for:

  * Operation durations
  * Network latencies

* Actual concurrency

  It's possible to limit the rate and measure the sustained actual concurrency

The *average* metrics output is being done periodically while a load step is running. The *summary* metrics output is
done once when a load step is finished. Also, it's possible to obtain the highest precision metrics (for each operation,
so called *I/O trace* records).

## 2.3. [Extension](src/main/java/com/emc/mongoose/base/env)

Mongoose is designed to be agnostic to the particular extensions implementations. This allows to support any storage,
scenario language, different load step kinds.

### 2.3.1. Load Steps

The load step is needed to define how to generate the load (operations type/order/ratio/etc).
Mongoose basically includes the linear load step implementation which may be considered as a straightforward way to 
generate a load. Other load step implementations allow to specify some custom and more complex load pattern. See the 
known load step extensions among the [bundle components](https://github.com/emc-mongoose/mongoose#bundle-contents) 
either among the [additional extensions](https://github.com/emc-mongoose/mongoose#additional-extensions).

### 2.3.2. Storage Drivers

The storage driver is used by Mongoose to interact with the given storage. It translates the Mongoose's abstract 
operations into the actual I/O requests and executes them. Mongoose basically includes the dummy storage driver only 
which does nothing actually and useful only for demo/testing purposes. See the known storage driver extensions among the
[bundle components](https://github.com/emc-mongoose/mongoose#bundle-contents) either among the 
[additional extensions](https://github.com/emc-mongoose/mongoose#additional-extensions).

### 2.3.3. Scenario Engine

Any Mongoose scenario may be written using any JSR-223 compliant scripting language. Javascript support is available
out-of-the-box.

## 2.4. Comparison With Similar Tools

* [COSBench](https://github.com/intel-cloud/cosbench)
* [LoadRunner](https://software.microfocus.com/en-us/products/loadrunner-load-testing/overview)
* [Locust](https://locust.io/)

### 2.4.1. General
|                   | Mongoose  | COSBench | LoadRunner         | Locust |
| ---               | :---:     | :---:    | :---:              | :---:  |
|**License**        |[MIT License](LICENSE)|[Apache 2.0](https://github.com/intel-cloud/cosbench/blob/master/LICENSE)|[Proprietary](https://en.wikipedia.org/wiki/LoadRunner)|[MIT License](https://github.com/locustio/locust/blob/master/LICENSE)|
|**Open Source**    |:heavy_check_mark:|:heavy_check_mark:    |:x:|  :heavy_check_mark:|

### 2.4.2. Purpose
|                   | Mongoose  | COSBench | LoadRunner | Locust |
| ---               | :---:     | :---:    | :---:      | :---:  |
|**[Load testing](https://en.wikipedia.org/wiki/Load_testing)** |:heavy_check_mark:|:heavy_check_mark:|:heavy_check_mark:|:heavy_check_mark:|
|**[Stress testing](https://en.wikipedia.org/wiki/Stress_testing)** |:heavy_check_mark:|:heavy_check_mark:|:heavy_check_mark:| TBD |
|**[Endurance testing](https://en.wikipedia.org/wiki/Soak_testing)**|:heavy_check_mark:| TBD |:heavy_check_mark:| TBD |

### 2.4.3. Scalability
|                                                    | Mongoose  | COSBench | LoadRunner | Locust |
| ---                                                | :---:     | :---:    | :---:      | :---:  |
|**Horizontal** (Distributed Mode)                 |:heavy_check_mark:|:heavy_check_mark:| TBD |:heavy_check_mark:|
|**Vertical** (Max sustained concurrency per instance)| 1_048_576 |[1024](http://cosbench.1094679.n5.nabble.com/how-many-connections-users-can-cosbench-create-to-test-one-swift-storage-tp325p326.html)| TBD |[1_000_000](https://locust.io/)|

### 2.4.4. Input
|                  | Mongoose  | COSBench | LoadRunner | Locust |
| ---              | :---:     | :---:    | :---:      | :---:  |
|**GUI**           |:x:|:heavy_check_mark:|:heavy_check_mark:|:heavy_check_mark:|
|**Parameterization**|:heavy_check_mark:| :heavy_check_mark: | TBD |:heavy_check_mark:(need to extend the functionality)|
|**Script language**| Any [JSR-223](https://en.wikipedia.org/wiki/Scripting_for_the_Java_Platform) compatible |[XML](https://en.wikipedia.org/wiki/XML)|[ANSI C, Java, .Net, JS](https://en.wikipedia.org/wiki/LoadRunner)|[Python](https://en.wikipedia.org/wiki/Python)|

### 2.4.5. Output
|                                        | Mongoose  | COSBench | LoadRunner | Locust |
| ---                                    | :---:     | :---:    | :---:      | :---:  |
|**Highest-resolution (per each op) metrics**|:heavy_check_mark:|:x:| TBD |:x:|
|**Saturation concurrency measurement**  |:heavy_check_mark:|:x:| TBD |:x:|

### 2.4.6. Load Generation Patterns
|                       | Mongoose  | COSBench | LoadRunner | Locust |
| ---                   | :---:     | :---:    | :---:      | :---:  |
|**[Weighted load](https://github.com/emc-mongoose/mongoose-load-step-weighted)**|:heavy_check_mark:| :heavy_check_mark:| TBD |:x:|
|**[Pipeline load](https://github.com/emc-mongoose/mongoose-load-step-pipeline)**|:heavy_check_mark:| :x:| TBD |:x:|
|**[Recycle mode](doc/design/recycle_mode)**|:heavy_check_mark: |:x:| TBD |:x:|

### 2.4.7. Storages Support

* **Note**: Locust and LoadRunner are not designed for the storage performance testing explicitly so they are excluded
from the table below

|                                            | Mongoose  | COSBench |
| ---                                        | :---:     | :---:    |
|**Supported storages**                      |<ul><li>Amazon S3</li><li>EMC Atmos</li><li>OpenStack Swift</li><li>Filesystem</li><li>HDFS</li><ul>|<ul><li>Amazon S3</li><li>Amplidata</li><li>OpenStack Swift</li><li>Scality</li><li>Ceph</li><li>Google Cloud Storage</li><li>Aliyun OSS</li><ul>|
|**Extensible to support custom storage API**|  :heavy_check_mark:   | :heavy_check_mark: |
