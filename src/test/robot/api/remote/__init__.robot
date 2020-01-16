*** Settings ***
Documentation   Mongoose Remote API suite
Force Tags      Remote API
Resource        MongooseContainer.robot
Library         OperatingSystem
Suite Setup     Start Mongoose Nodes
Suite Teardown  Remove Mongoose Nodes

