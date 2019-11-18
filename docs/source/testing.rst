Testing Framework
=================

MockFogLoad allows application developers to specify test cases which will be excuted by the orchestrator. The tests are separated into stages, each stage has a time in seconds associated with it. The execution timing on the Fog Nodes will be best effort while the timing on the generators will be exact.
The testfiles have to be provided to the orchestrator in a JSON format.

Example Test
------------
The following JSON is an example test. It will turn off all interfaces on the node with id t2.micro_1 after 10 seconds. After another 10 seconds, all interfaces will be turned back on::

    {
        "testName": "AllDown",
        "stages": [
            {
                "time": 10,
                "node": [
                    {
                        "id": "t2.micro_1",
                        "interface": [
                            {
                                "id": "all",
                                "status": "down"
                            }
                        ]
                    }
                ]
            },
            {
                "time": 10,
                "node": [
                    {
                        "id": "t2.micro_1",
                        "interface": [
                            {
                                "id": "all",
                                "status": "up"
                            }
                        ]
                    }
                ]
            }
        ]
    }

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
            * status: Status of the interface, can be either on or off.
            * bandwidth: Maximum available throughput of the interface.
            * buffer: Size of the queue buffer of the interface.
            * routes: Set routes TBD/TODO
        * cpu: Set maximum CPU clock speed.
        * memory: Set maximum available memory.
        * storage: Set storage size of the node.
        * generators: A list of generators running on the node.
            * id: ID of the generator, can be all to select all generators running on a given node.
            * frequency: Set the frequency on how often the generator should send out data.
            * granularity: Set how granular the generated data is.
            * active: Turn the generator off or on entirely.
            * target: ID of a fog node to which the generator should send its data.
            * seed: Restart the generator with a new specified seed.
            * custom: TBD/TODO Custom generator parameters, provided in a (key,value) format.
