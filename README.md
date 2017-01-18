# onos-fattree
ONOS application for managing the fat tree topology. 

###Contents:
- ONOS Application for Fat-Tree
- Fat-Tree Topology Generator

###Pre-requirement:
- ONOS Development Environment Setup:(ONOS WIKI: https://wiki.onosproject.org/display/ONOS/Development+Environment+Setup)
- Mininet:(http://mininet.org/)

###HOW TO USE:
1. build onos-fattree App
- Checkout ONOS Source Code 1.7.0
- Move onos-fattree to ONOS_SOURCE/Apps floder
- In "FattreeApplication.java" update MININET_IP for your Mininet server ip
- In terminal, go to onos-fattree directory
- Maven build.(mvn clean install)
- After build success, you will find .oar file in target folder.

2. run ONOS
- run ONOS(ok clean)
- login to ONOS Web GUI(http://localhost:8181/onos/ui) usr/passwd: karaf/karaf
- click menu(left-top)->Applications->add->click .oar file which is generated in Step1
- wait for topology is built.

3. build topology

- In Mininet environment(recommend environment: Ubuntu)
- Modify ONOS IP Address in topo_fattree.
- Run topology generator file(sudo ./topo_fattree.py)

4. run ONOS-Fattree App
- Goto ONOS Web GUI Application Management Page(Step2.3)
- Select onos-fattree app
- click activate

5. test
- In Mininet, test pingall
