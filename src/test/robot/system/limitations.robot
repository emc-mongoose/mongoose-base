*** Settings ***
Documentation  Mongoose Limitations tests
Force Tags     Limitations
Resource       Common.robot
Library        OperatingSystem
Library        RequestsLibrary
Library        Collections
Library        OperatingSystem
Library        RequestsLibrary
Library        String

*** Variables ***
${DATA_DIR}  src/test/robot/system/data
${MONGOOSE_LOGS_URI_PATH}  /logs
${COUNT_LIMIT}  1000
${LOGGER_NAME}  metrics.FileTotal

*** Test Cases ***
Should Stop After 1000 Operation
    ${data} =  Make Start Request Payload Without Scenario Part
    ${resp_start} =  Start Mongoose Scenario  ${data}
    Should Be Equal As Strings  ${resp_start.status_code}  202
    ${resp_etag_header} =  Get From Dictionary  ${resp_start.headers}  ${HEADER_ETAG}
    ${uri_path} =  Catenate  ${MONGOOSE_LOGS_URI_PATH}/${STEP_ID}/${LOGGER_NAME}
    Wait Until Keyword Succeeds  10x  1s  Should Return Status  ${uri_path}  200
    ${resp} =  Get Request  mongoose_node  ${uri_path}
    Should Be Equal As Strings  ${resp.status_code}  200
    Should Have Lines  ${resp.text}  ${COUNT_LIMIT}
    ${resp_stop} =  Stop Mongoose Scenario Run  ${resp_etag_header}


*** Keywords ***
Make Start Request Payload Without Scenario Part
    ${defaults_data} =  Get Binary File  ${DATA_DIR}/count_limit.yaml
    &{data} =  Create Dictionary  defaults=${defaults_data}
    [Return]  ${data}
