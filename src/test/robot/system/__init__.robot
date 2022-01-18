*** Settings ***
Documentation  Mongoose Limitations tests
Force Tags      Limitations
Resource        ../lib/MongooseContainer.robot
Library         OperatingSystem
Suite Setup     Start Mongoose Nodes
Suite Teardown  Remove Mongoose Nodes
