Testing Framework
=================

MockFogLoad allows application developers to specify test cases which will be excuted by the orchestrator. The tests are separated into stages, each stage has a time in seconds associated with it. The execution timing on the Fog Nodes and generators will be exact.
The testfiles have to be provided to the orchestrator in a YAML format.

The mapping of Node ids to IP addresses has to be provided in a map file. 

Example Test
------------
The following YAML is an example test. The first stage will spawn a generator of type HeartRate on the Node with ID Node1. It will set the frequency to 30 and the destination of the events to Node2.
The second stage will turn off the generator. ::


    testName: HeartRate
    stages:
    - id: 1
      time: 10
      node:
      - id: Node1
        generators:
        - id: HR1
          kind: HeartRate
          frequency: 30
          endpoint: Node2
          active: true
    - id: 2
      time: 10
      node:
      - id: Node1
        generators:
        - id: HR1
          active: false  

The mapping of the nodes to IPs for this case would look like this::


    nodes:
    - id: Node1
      ip: 0.0.0.0:8080
    - id: Node2
      ip: 0.0.0.0:8081

Testing Parameters
------------------

The following parameters can be specified by applications for testing:

* testName: The name of the test.
* stages: A list of stages the test is supposed to go through.
    * id: ID of the stage.
    * time: After what time interval (in seconds) the changes should occur.
    * node: A list of fog nodes on which the changes should occur on.
        * id: ID of the node, can be all to select all fog nodes.
        * interface(optional): A list of interfaces.
            * id: ID of the interface, can be all to select all interfaces on a given node.
            * status(optional): Status of the interface, can be either on or off.
            * bandwidth(optional): Maximum available throughput of the interface.
            * buffer(optional): Size of the queue buffer of the interface.
            * routes(optional): Set routes TBD/TODO
        * cpu(optional): Set maximum CPU clock speed.
        * memory(optional): Set maximum available memory.
        * storage(optional): Set storage size of the node.
        * generators(optional): A list of generators running on the node.
            * id: ID of the generator, can be all to select all generators running on a given node.
            * frequency(optional): Set the frequency on how often the generator should send out data.
            * granularity(optional): Set how granular the generated data is.
            * active(optional): Turn the generator off or on entirely.
            * endpoint(optional): ID of a fog node to which the generator should send its data.
            * seed(optional): Restart the generator with a new specified seed.
            * format_string(optional): Format in which the generator will send data.
            * protocol(optional): Which transport protocol should be used for communication. Can be UDP, CoAP or HTTP2. Default: HTTP2.
            * custom(optional): TBD/TODO Custom generator parameters, provided in a (key,value) format.
