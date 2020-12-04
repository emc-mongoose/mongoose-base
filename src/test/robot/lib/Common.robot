*** Settings ***
Documentation  Commons Keywords



*** Variables ***
${DATA_DIR}                src/test/robot/api/remote/data
${MONGOOSE_RUN_URI_PATH}   /run
${MONGOOSE_REST_PORT}      9999
${MONGOOSE_RMI_PORT}       1099
${MONGOOSE_ADD_REST_PORT}  9990
${MONGOOSE_ADD_RMI_PORT}   1098
${SESSION_NAME}            mongoose_node
${ADD_SESSION_NAME}        mongoose_add_node
${STEP_ID}                 robotest
${HEADER_ETAG}             ETag



*** Keywords ***
Start Mongoose Scenario
    [Arguments]  ${data}
    ${resp} =  Post Request  ${SESSION_NAME}  ${MONGOOSE_RUN_URI_PATH}  files=${data}
    Log  ${resp.status_code}
    [Return]  ${resp}

Stop Mongoose Scenario Run
    [Arguments]  ${etag}
    &{req_headers} =  Create Dictionary  If-Match=${etag}
    ${resp} =  Delete Request  ${SESSION_NAME}  ${MONGOOSE_RUN_URI_PATH}  headers=${req_headers}
    Log  ${resp.status_code}
    [Return]  ${resp}

Should Return Status
    [Arguments]  ${uri_path}  ${expected_status}
    ${resp} =  Get Request  ${SESSION_NAME}  ${uri_path}
    Should Be Equal As Strings  ${resp.status_code}  ${expected_status}

Should Include String
    [Arguments]  ${result}  ${pattern}
    ${lines} =    Get Lines Matching Pattern    ${result}    ${pattern}
    ${count} =  Get Line Count  ${lines}
    Should Be True  ${count}>0
