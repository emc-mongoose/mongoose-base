*** Settings ***
Documentation  Mongoose Remote API suite
Force Tags  Remote API
Library  OperatingSystem
Library  RequestsLibrary
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


Start Mongoose Node
    [Arguments]  ${session_name}  ${port}
    ${image_version} =  Get Environment Variable  MONGOOSE_IMAGE_VERSION
    # ${service_host} should be used instead of the "localhost" in GL CI
    ${service_host} =  Get Environment Variable  SERVICE_HOST
    ${cmd} =  Catenate  SEPARATOR= \\\n\t
    ...  docker run
    ...  --detach
    ...  --name mongoose_node
    ...  --publish ${port}:${MONGOOSE_NODE_PORT}
    ...  ${MONGOOSE_IMAGE_NAME}:${image_version}
    ...  --load-step-id=robotest --run-node
    ${std_out} =  Run  ${cmd}
    Log  ${std_out}
    Create Session  ${session_name}  http://${service_host}:${port}  debug=1  timeout=1000  max_retries=10


Remove Mongoose Nodes
    Delete All Sessions
    Run  docker stop ${SESSION_NAME}
    Run  docker rm ${SESSION_NAME}
    Run  docker stop ${ADD_SESSION_NAME}
    Run  docker rm ${ADD_SESSION_NAME}
