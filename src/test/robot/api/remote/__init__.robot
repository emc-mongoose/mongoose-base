*** Settings ***
Documentation  Mongoose Remote API suite
Force Tags  Remote API
Resource   MongooseContainer.robot
Library  OperatingSystem
Suite Setup  Start Mongoose Nodes
Suite Teardown  Remove Mongoose Nodes

*** Variables ***
${MONGOOSE_IMAGE_NAME} =  emcmongoose/mongoose-base
${MONGOOSE_NODE_PORT} =  9999
${MONGOOSE_ADD_NODE_PORT} =  9998
${SESSION_NAME} =  mongoose_node
${ADD_SESSION_NAME} =  mongoose_add_node

*** Keywords ***
Start Mongoose Nodes
    Start Entry Mongoose Node
    Start Additional Mongoose Node


Start Entry Mongoose Node
    Start Mongoose Node  ${SESSION_NAME}  ${MONGOOSE_NODE_PORT}


Start Additional Mongoose Node
    Start Mongoose Node  ${ADD_SESSION_NAME}  ${MONGOOSE_ADD_NODE_PORT}


Remove Mongoose Nodes
    Delete All Sessions
    Run  docker stop ${SESSION_NAME}
    Run  docker rm ${SESSION_NAME}
    Run  docker stop ${ADD_SESSION_NAME}
    Run  docker rm ${ADD_SESSION_NAME}
