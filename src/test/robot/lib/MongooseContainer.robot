*** Settings ***
Documentation  Mongoose Container Keywords
Resource       Common.robot
Library        OperatingSystem
Library        RequestsLibrary



*** Variables ***
${LOG_DIR}                      build/log
${MONGOOSE_CONTAINER_DATA_DIR}  /data
${MONGOOSE_CONTAINER_NAME}      mongoose
${MONGOOSE_IMAGE_NAME}          emcmongoose/mongoose-base



*** Keywords ***
Start Mongoose Nodes
    Start Entry Mongoose Node
    Start Additional Mongoose Node

Start Entry Mongoose Node
    Start Mongoose Node  ${SESSION_NAME}  ${MONGOOSE_REST_PORT}  ${MONGOOSE_RMI_PORT}

Start Additional Mongoose Node
    Start Mongoose Node  ${ADD_SESSION_NAME}  ${MONGOOSE_ADD_REST_PORT}  ${MONGOOSE_ADD_RMI_PORT}

Remove Mongoose Nodes
    Delete All Sessions
    Run  docker stop ${SESSION_NAME}
    Run  docker rm ${SESSION_NAME}
    Run  docker stop ${ADD_SESSION_NAME}
    Run  docker rm ${ADD_SESSION_NAME}

Start Mongoose Node
    [Arguments]  ${session_name}  ${rest_port}  ${rmi_port}
    ${image_version} =  Get Environment Variable  MONGOOSE_IMAGE_VERSION
    # ${service_host} should be used instead of the "localhost" in GL CI
    ${service_host} =  Get Environment Variable  SERVICE_HOST
    ${cmd} =  Catenate  SEPARATOR= \\\n\t
    ...  docker run
    ...  --detach
    ...  --name ${session_name}
    ...  --publish ${rest_port}:${MONGOOSE_REST_PORT}
    ...  --publish ${rmi_port}:${MONGOOSE_RMI_PORT}
    ...  ${MONGOOSE_IMAGE_NAME}:${image_version}
    ...  --load-step-id=${STEP_ID} --run-node
    ${std_out} =  Run  ${cmd}
    Log  ${std_out}
    Create Session  ${session_name}  http://${service_host}:${rest_port}  debug=1  timeout=1000  max_retries=10
    Get Docker Logs From Container With Name ${session_name}


Execute Mongoose Scenario
    [Arguments]  ${shared_data_dir}  ${env}  ${args}
    ${docker_env_vars} =  Evaluate  ' '.join(['-e %s=%s' % (key, value) for (key, value) in ${env}.items()])
    ${host_working_dir} =  Get Environment Variable  HOST_WORKING_DIR
    Log  Host working dir: ${host_working_dir}
    ${mongoose_version} =  Get Environment Variable  MONGOOSE_VERSION
    ${image_version} =  Get Environment Variable  MONGOOSE_IMAGE_VERSION
    ${cmd} =  Catenate  SEPARATOR= \\\n\t
    ...  docker run
    ...  --name ${MONGOOSE_CONTAINER_NAME}
    ...  --network host
    ...  ${docker_env_vars}
    ...  --volume ${host_working_dir}/${shared_data_dir}:${MONGOOSE_CONTAINER_DATA_DIR}
    ...  --volume ${host_working_dir}/${LOG_DIR}:/root/.mongoose/${mongoose_version}/log
    ...  ${MONGOOSE_IMAGE_NAME}:${image_version}
    ...  ${args}
    ${std_out} =  Run  ${cmd}
    [Return]  ${std_out}


Get Docker Logs From Container With Name ${name}
     ${cmd} =  Catenate  docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' ${name}
     ${std_out} =  Run  ${cmd}
     Log  ${std_out}

     ${cmd} =  Catenate  docker logs ${name}
     ${std_out} =  Run  ${cmd}
     Log  ${std_out}