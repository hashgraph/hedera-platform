# Hashgraph Platform Source

### License

(c) 2016-2022 Swirlds, Inc.

This software is owned by Swirlds, Inc., which retains title to the software. This software is protected by various 
intellectual property laws throughout the world, including copyright and patent laws. This software is licensed and 
not sold.  You must use this software only in accordance with the terms of the Hashgraph Open Review License at 

[https://github.com/hashgraph/swirlds-open-review/raw/master/LICENSE.md](/LICENSE.md)

SWIRLDS MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF THIS SOFTWARE, EITHER EXPRESS OR IMPLIED, 
INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, 
OR NON-INFRINGEMENT.


### Acknowledgments

Portions of this Swirlds, Inc. Software may utilize the following copyrighted material, the use of which is hereby 
acknowledged.

The full list of acknowledgements is available at 
[https://github.com/hashgraph/swirlds-open-review/raw/master/acknowledgments.html](/acknowledgments.html)


### Building the Source Code

#### Minimum Requirements

- **Oracle OpenJDK 17**
  - Available from [http://jdk.java.net/17/](http://jdk.java.net/17/)
- **Apache Maven 3**
  - Available from [http://maven.apache.org/download.cgi](http://maven.apache.org/download.cgi)
- **PostgreSQL 10**
  - Please refer to the [PostgreSQL Docker Setup Guide](docs/psql-docker-setup-guide.md)
  
#### Standard Build Command

The entire project may be built by executing `mvn deploy` from the root of this repository. 

##### **NOTE:** 
The `deploy` phase is required to produce a complete build. 
The maven `compile` and `install` phases will not install all necessary dependencies. 

#### Contributing

Please send any comments or suggestions or suggested code changes to [software@swirlds.com](mailto:software@swirlds.com).<br />
By doing so, you agree to grant Swirlds the right and license to use any such feedback as set out in the Hashgraph Open Review License.<br />
To report bugs, and possibly be rewarded through the Bug Bounty program, please see https://www.hedera.com/bounty
