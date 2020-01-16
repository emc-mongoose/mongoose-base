*** Settings ***
Documentation  Commons Remoute API Keywords

*** Variables ***
${DATA_DIR}  src/test/robot/api/remote/data
${HEADER_ETAG}  ETag
${MONGOOSE_RUN_URI_PATH}  /run
${STEP_ID}  robotest

*** Keywords ***
Start Mongoose Scenario
    [Arguments]  ${data}
    ${resp} =  Post Request  mongoose_node  ${MONGOOSE_RUN_URI_PATH}  files=${data}
    Log  ${resp.status_code}
    [Return]  ${resp}

Stop Mongoose Scenario Run
    [Arguments]  ${etag}
    &{req_headers} =  Create Dictionary  If-Match=${etag}
    ${resp} =  Delete Request  mongoose_node  ${MONGOOSE_RUN_URI_PATH}  headers=${req_headers}
    Log  ${resp.status_code}
    [Return]  ${resp}

Should Return Status
    [Arguments]  ${uri_path}  ${expected_status}
    ${resp} =  Get Request  mongoose_node  ${uri_path}
    Should Be Equal As Strings  ${resp.status_code}  ${expected_status}
