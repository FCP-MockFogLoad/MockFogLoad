mockfog_application
=========

Rolls out a ping application and configures it using the hostvars set by mockfog_topology.
The respective playbook is placed in the parent directory.

Tags:
- deploy: roll out and start application, delete priorly running instances and data
- collect: collect logs, keep application running

### Requirements

- needs to be run after mockfog_network

### Role Variables

A playbook needs to be run with the following parameters:
```bash
--key-file=XXXX
--ssh-common-args="-o StrictHostKeyChecking=no"
```

Assume that the playbook supplies the testbed definition in the form of a vars file, if bootstrap tag is used.
