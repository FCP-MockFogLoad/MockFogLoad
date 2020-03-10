Testing Framework
=================

MockFogLoad allows application developers to specify test cases which will be excuted by the orchestrator. The tests are separated into stages, each stage has a time in seconds associated with it. The execution timing on the Fog Nodes and generators will be exact.
The testfiles have to be provided to the orchestrator in a YAML format.

The mapping of Node ids to IP addresses has to be provided in a map file. 

Example Test
------------
The following YAML is an example test. It will set the bandwith on the interface docker0 on nodes with ids application_layer1_1 and generator1 to 1gbps. It will also spawn a heartrate generator on generator1 with the given configuration::

    testName: HeartRate
    stages:
    - id: 1
        time: 1
        node:
        - id: application_layer1_1
            interfaces:
            - id: docker0
                bandwidth: 1gbps

        - id: generator1
            interfaces:
            - id: docker0
                bandwidth: 1gbps

        - id: generator1
            generators:
            - id: HR1
                kind: HeartRate
                events_per_second: 30
                endpoint: application_layer1_1
                endpoint_port: 30444
                active: true
                format_string: '{"source":"peter","time":${timestamp},"value":${heartRate},"type":"heartRate"}'
                protocol: HTTP
                seed: 30

The mapping of the nodes to IPs for this case would look like this::


    nodes:
    - id: application_layer1_1
      ip: 0.0.0.0
    - id: generator1
      ip: 0.0.0.0

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
            * active: Status of the interface, can be either true or false.
            * bandwidth:(optional) Maximum available throughput of the interface.
            * delay(optional): Set a delay in ms
            * loss(optional): Set a percentage of how many packets get dropped
        * app(optional): A list of containers.
            * id: Name of the container to be configured.
            * cpu(optional): Set CPU shares.
            * memory(optional): Set maximum available memory.
        * generators(optional): A list of generators running on the node.
            * id: ID of the generator, can be all to select all generators running on a given node.
            * kind(optional): Set the type of generator. 
            * frequency(optional): Set the frequency on how often the generator should send out data.
            * active(optional): Turn the generator off or on entirely.
            * endpoint(optional): ID of a fog node to which the generator should send its data.
            * endpoint_port(optional): Port of the remote to which the generator will send its data.
            * seed(optional): Restart the generator with a new specified seed.
            * format_string(optional): Set the format in which the generator will send data.
            * protocol(optional): Set the protocol with which the generator will send data. Options are HTTP, UDP and CoAP.
