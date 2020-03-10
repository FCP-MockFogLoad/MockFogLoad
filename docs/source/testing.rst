Testing Framework
=================

MockFogLoad allows application developers to specify test cases which will be excuted by the orchestrator. The tests are separated into stages, each stage has a time in seconds associated with it. The execution timing on the Fog Nodes will be best effort while the timing on the generators will be exact.
The testfiles have to be provided to the orchestrator in a JSON format.

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

Testing Parameters
------------------

The following parameters can be specified by applications for testing:

* testName: The name of the test.
* stages: A list of stages the test is supposed to go through.
    * time: After what time interval (in seconds) the changes should occur.
    * node: A list of fog nodes on which the changes should occur on.
        * id: ID of the node, can be all to select all fog nodes.
        * interface: A list of interfaces.
            * id: ID of the interface, can be all to select all interfaces on a given node.
            * active: Status of the interface, can be either true or false.
            * bandwidth: Maximum available throughput of the interface.
            * delay: Set a delay in ms
            * loss: Set a percentage of how many packets get dropped
        * app: A list of containers.
            * id: Name of the container to be configured.
            * cpu: Set CPU shares.
            * memory: Set maximum available memory.
        * generators: A list of generators running on the node.
            * id: ID of the generator, can be all to select all generators running on a given node.
            * kind: Set the type of generator. 
            * frequency: Set the frequency on how often the generator should send out data.
            * active: Turn the generator off or on entirely.
            * endpoint: ID of a fog node to which the generator should send its data.
            * endpoint_port: Port of the remote to which the generator will send its data.
            * seed: Restart the generator with a new specified seed.
            * format_string: Set the format in which the generator will send data.
            * protocol: Set the protocol with which the generator will send data. Options are HTTP, UDP and CoAP.
