/*
 * Offload RPC Library
 *
 * Copyright 2022 Ashish Chaudhari
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

library (
  author: "achaudhari",
  category: "driverUtilities",
  description: "Offload RPC Library",
  name: "offrpclib_v1",
  namespace: "offrpclib"
)

def rpcCall(remoteUri, method, params, timeout) {
    fCallOutput = [reqMethod:method, reqTimestamp:new Date().format("yyyy-MM-dd HH:mm:ss"),
                   respStatus:-1, respResult:"Input argument error"]

    Map postParams = [
        uri: remoteUri,
        contentType: "application/json",
        requestContentType: 'application/json',
        body: ["jsonrpc": "2.0", "id": 0, "method": method, "params": params],
        timeout: timeout
    ]

    try {
        httpPost(postParams) { resp ->
            respStatus = -100
            respResult = ""
            if (resp.status >= 300) {
                respResult = "HTTP POST Status = ${resp.status}"
                log.error respResult
            } else {
                try {
                    respStatus = -101
                    resp_blob = resp.getData()
                    if (resp_blob.containsKey("error")) {
                        respStatus = resp_blob.error.code
                        respResult = "${resp_blob.error.message}, ${resp_blob.error.data}"
                        log.error "JSONRPC Error = ${resp_blob.error}}"
                    } else if (resp_blob.containsKey("result")) {
                        respStatus = 0
                        respResult = resp_blob.result
                    }
                } catch (groovy.json.JsonException ex) {
                    log.warn "Response JSON was malformed"
                }
            }
            fCallOutput['respStatus'] = respStatus
            fCallOutput['respResult'] = respResult
            fCallOutput['respTimestamp'] = new Date().format("yyyy-MM-dd HH:mm:ss")
        }
    } catch (Exception e) {
        fCallOutput['respStatus'] = -102
        fCallOutput['respResult'] = e.message
        log.warn "HTTP POST Status = ${e.message}"
    }
    return fCallOutput
}