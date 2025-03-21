/*
 * SolArk Inverter
 *
 *
 *  Change History:
 *
 *      Date          Source        Version     What                                              URL
 *      ----          ------        -------     ----                                              ---
 *      2023-09-24    pentalingual  0.1.0       Starting version
 *      2024-01-02    pentalingual  0.2.0       Added Token Refresh
 *      2024-02-25    pentalingual  0.3.0       Added inverter Details & logging
 *      2024-06-11    pentalingual  0.4.0       Switched to MySolArk
 *      2024-07-04    pentalingual  0.4.2       Updated API Report Handling
 *      2024-09-07    pentalingual  0.4.3       Updated link descriptions and improved error logging
 *      2025-01-04    StarkTemplar  0.4.4       Updated battery status, source of power logic, inverter limit logic.
 *      2025-01-05    StarkTemplar  0.4.5       Updated attribute name from Power to Load. Added additional API calls to gather usage stats for the day/total.
 *      2025-01-11    StarkTemplar  0.4.6       Bug fixes. Update inverter calculation. Schedule to refresh token before token expires.
 *      2025-01-12    StarkTemplar  0.4.7       Update tile output to round to 0 decimal places. Add monthly data and monthly tile.
 *      2025-01-13    StarkTemplar  0.4.8       Add presence capability to grid. Will allow for easier alerting when grid goes down.
 *      2025-01-21    StarkTemplar  0.4.9       Bug fixes. Testing single threaded option. Converted all outputted values to kW or kWh. Updated battery inverter check.
 *      2025-01-27    StarkTemplar  0.5.0       Updated inverter limit check. Previous calculation was adding the currents at 110v. The Solark limitation is at 240v for the 12k and 15k models.
 *      2025-01-29    StarkTemplar  0.5.1       Updated Grid down detection.
 *      2025-02-01    StarkTemplar  0.5.2       Highlight grid number when grid presence is not present.
 *      2025-03-17    StarkTemplar  0.5.3       Update for solark API changes
 */

static String version() { return '0.5.3' }

metadata {
    definition(
            name: "SolArk Inverter",
            namespace: "StarkTemplar",
            author: "StarkTemplar",
            description: "Leverages the MySolArk API to update Hubitat with your SolArk inverter status",
            category: "Integrations",
            singleThreaded: true,
            importUrl: "https://raw.githubusercontent.com/StarkTemplar/Hubitat/refs/heads/main/Solar/SolArk_Inverter.groovy"
    )  {
        capability "Initialize"
        capability "Refresh"
        capability "PowerMeter"
        capability "PowerSource"
        capability "CurrentMeter"
        capability "Battery"
        capability "Presence Sensor"

        attribute "lastresponsetime", "string"
        attribute "PVPower", "number"
        attribute "PVPowerToday", "number"
        attribute "PVLastMonth", "number"
        attribute "PVThisMonth", "number"
        attribute "PVPowerAlltime", "number"
        attribute "GridPowerDraw", "number"
        attribute "GridImportToday", "number"
        attribute "GridImportLastMonth", "number"
        attribute "GridImportThisMonth", "number"
        attribute "GridImportAlltime", "number"
        attribute "GridExportToday", "number"
        attribute "GridExportLastMonth", "number"
        attribute "GridExportThisMonth", "number"
        attribute "GridExportAlltime", "number"
        attribute "Load", "number"
        attribute "LoadToday", "number"
        attribute "LoadLastMonth", "number"
        attribute "LoadThisMonth", "number"
        attribute "LoadAlltime", "number"
        attribute "battery", "number"
        attribute "BatteryDraw", "number"
        attribute "BatteryStatus", "string"
        attribute "BatteryChargeToday", "number"
        attribute "BatteryChargeLastMonth", "number"
        attribute "BatteryChargeThisMonth", "number"
        attribute "BatteryChargeAlltime", "number"
        attribute "BatteryDischargeToday", "number"
        attribute "BatteryDischargeLastMonth", "number"
        attribute "BatteryDischargeThisMonth", "number"
        attribute "BatteryDischargeAlltime", "number"
        attribute "GeneratorDraw", "number"
        attribute "GeneratorToday", "number"
        attribute "GeneratorLastMonth", "number"
        attribute "GeneratorThisMonth", "number"
        attribute "GeneratorAlltime", "number"
        attribute "ComprehensiveTile", "string"
        attribute "MonthlyTile", "string"
        attribute "presence", "string"
    }

    preferences {
        input name: "Blank0",  title: "<center><strong>This driver will maintain an API connection with the MySolArk portal to update Hubitat with your latest solar/battery inverter details.</strong></center>", type: "hidden"
        input name: "Instructions", title: "<center>**********<br><i>To make it work, you'll need to figure out your plant ID to associate with this driver, and provide the API your MySolArk username and password</i></center>", type: "hidden"
        input name: "Blank1",  title: "<center>**********<br>The Plant ID is at the end of the URL when you navigate to the <a href='https://www.mysolark.com/plants/' target='_blank'>Plant Overview</a> page and click into your desired power plant.</center>",  type: "hidden"
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false
        input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
        input name: "refreshSched", type: "int", title: "Refresh every how many minutes?", defaultValue: 15  
        input name: "plantID", type: "string", title: "MySolArk Plant ID", description: "<i><small>The Plant ID is at the end of the URL right before the '/2' when you login and navigate to the desired plant https://www.mysolark.com/plants/overview/</i></small><strong>?????</strong><i><small>/2</i></small>", defaultValue: null
        input name: "Username", type: "string", title: "MySolArk Username", defaultValue: null
        input name: "Password", type: "password", title: "MySolArk Password", hidden: true, defaultValue: null
    }
}


def initialize() {
     log.info "Initializing the MySolArk service..."
     state.clear()
     unschedule() //clear all previous scheduled jobs
     state.Amperage = "the AC output being inverted from DC Power Sources (grid/gen current is not inverted)"
     state.Load = "the total number of Watts being drawn by the load/home"
     def getTokenResult = getToken(true)
     if ( getTokenResult > 0 ) {
        runIn(getTokenResult,getToken)
        if (logEnable) log.debug("schedule to get new token in ${getTokenResult} seconds")
        runIn(10,refresh)
        schedule("0 0/${refreshSched} * * * ?", refresh)
        log.info "Refreshing every ${refreshSched} minutes. Debug logging is: ${logEnable}."
     } else {
        log.info "getToken error. Skipping further requests. Enable debugging for further info."
     }
}
                                
def updated() {
    log.info "Preferences saved."
    initialize()
}

def refresh() {
    
    try{
        if ( getPlantDetails() ) {
            def (battCharge, pvPower, gridPower, genPower) = getFlow()
            getAmperage(battCharge, pvPower, gridPower, genPower)
            getPVDetails()
            getGridDetails()
            getLoadDetails()
            getBattDetails()
            getGenDetails()
            getMonthlyDetails()
            runIn(10,updateTiles)
        } else {
            log.error "getPlantDetails error. skipping further requests. enable debugging for further detail."
        }
    }
    catch (exception) {
        if (logEnable) {
            log.debug exception
        }
        log.error "refresh error. enable debugging for further detail."
    }
}

def getToken(refreshToken = false) {
    if ( refreshToken == true ) {
        body1 = ['username':Username,'password':Password,'grant_type':'password','client_id':'csp-web']    
    } else {
        body1 = ['username':Username,'password':Password,'grant_type':'refresh_token','refresh_token':state.xTokenRefreshKeyx,'client_id':'csp-web']
    }
    // def URIa = "https://openapi.inteless.com/v1/oauth/token"
    // def URIb = "https://pv.inteless.com/api/v1/oauth/token"
    def URIc = "https://api.solarkcloud.com/oauth/token"
    def paramsTOK = [
        uri: URIc,
        headers: [
            'Origin': 'https://www.mysolark.com',
            'Referer': 'https://www.mysolark.com'
        ],
        body: body1
    ]
    
    def tokenRefreshJob //must be created outside of try block
    //* Catch error and define. 443. 401, read timed out
    try {
        httpPostJson(paramsTOK, { resp -> 
            if (logEnable) log.debug(resp.getData().data)
            
            state.xTokenKeyx = resp.getData().data.access_token
            state.xTokenRefreshKeyx = resp.getData().data.refresh_token
            def tokenExpiration = resp.getData().data.expires_in as Integer
            if (logEnable) log.debug("token expiration: ${tokenExpiration}")

            tokenRefreshJob = tokenExpiration - ((refreshSched.toInteger() * 60) / 2) as Integer
        })
    }
    catch (groovyx.net.http.HttpResponseException exception) {
        if (logEnable) {
            log.debug exception
        }
        def httpError = exception.getStatusCode()
        if ( httpError == 401 ) {
            log.error("http 401 - token has expired.")
        } else {
            log.error("unable to login. http error ${httpError}. enable debugging for further detail.")
        }
        return false
     }
    catch (Exception) {
        if (logEnable) {
            log.debug exception
        }
        log.error "unable to login. This could be due to an invalid username/password, or MySolArk site may be down. enable debugging for further detail."
        return false
    }

    return tokenRefreshJob
}

def getPlantDetails() {
    def key = "Bearer ${state.xTokenKeyx}"
    body5 = ['page': 1, 'limit': 1, 'status': 1, 'stationId':plantID, 'type': -2]
    //if (logEnable) log.debug("body5-{body5}")
    def paramsInitial = [  
        uri: "https://api.solarkcloud.com/api/v1/plant/${plantID}/inverters",
        headers: [ 'Authorization' : key], 
        query: body5
        ]
       try { 
        httpGet(paramsInitial,  { resp ->
            if (logEnable) log.debug(resp.getData().data)
            
            def systemModel = resp.getData().data.infos.ratePower[0]
            state.SystemSize =  systemModel.toInteger()
            state.inverterSN = resp.getData().data.infos.sn[0]
            if (logEnable) log.debug("systemSize-{state.SystemSize}")

            /* inverter's AC Limit is different depending on models
                * 12K - 37.5A
                * 15K - 62.5A total, 50A batteries only
                * 30K - 83.4A
                * 60K - 72.3A
            */
            if ( state.SystemSize == 12000 ) {
                state.inverterLimit = 37
                state.systemVoltage = 240
            } else if ( state.SystemSize == 15000 ) {
                state.inverterLimit = 62
                state.systemVoltage = 240
            } else if ( state.SystemSize == 30000 ) {
                state.inverterLimit = 83
                state.systemVoltage = 208
            } else if ( state.SystemSize == 60000 ) {
                state.inverterLimit = 72
                state.systemVoltage = 480
            } else {
                state.inverterLimit = 0
                state.systemVoltage = 240
            }

            return true
        })
        }
    catch (groovyx.net.http.HttpResponseException exception) {
        if (logEnable) {
            log.debug exception
        }
        def httpError = exception.getStatusCode()
        if ( httpError == 401 ) {
            log.error("http 401 - token has expired. trying to refresh token")
            if ( state.xTokenRefreshKeyx ) {
                getToken()
            }
        } else {
            log.error("http error ${httpError}. unable to return the inverter for this plant you may need to check that the plant id ${plantID} is correct. enable debugging for further detail.")
        }
        return false
    } 
    catch (exception) {
        if (logEnable) {
            log.debug exception
        }
        log.error("unable to return the inverter for this plant you may need to check that the plant id ${plantID} is correct. enable debugging for further detail.")
        return false
     }
}

def getFlow()  {
    def key = "Bearer ${state.xTokenKeyx}"
    def paramsEnergy = [  
        uri: "https://api.solarkcloud.com/api/v1/plant/energy/${plantID}/flow",
        headers: [ 'Authorization' : key ],
        requestContentType: "application/x-www-form-urlencoded"
    ]
    try {
        httpGet(paramsEnergy, { resp ->
            if (logEnable) log.debug(resp.getData().data)
            
            boolean solar = resp.getData().data.pvPower > 120
            boolean gen = resp.getData().data.genPower > 120
            
            float curr = ((resp.getData().data.loadOrEpsPower - resp.getData().data.gridOrMeterPower)/ 120 )
            float amperesEst = curr.round(2)
            
            float homePower = resp.getData().data.loadOrEpsPower
            float gridPower = resp.getData().data.gridOrMeterPower
            if (resp.getData().data.toGrid) gridPower = -gridPower
            boolean grid = gridPower > 120
            
            float pvPower = resp.getData().data.pvPower
            float genPower = resp.getData().data.genPower

            int battSOC = resp.getData().data.soc
            float battCharge = resp.getData().data.battPower
            if (resp.getData().data.toBat) battCharge = battCharge* -1
            boolean battPower = battCharge > 120
            
            def textSource = ""
            def newSource = ""
            
            if ((grid && solar) || (gen && solar) || (battPower && solar) || (gen && battPower)) {
                newSource = "mixed"
                textSource = "mixed"
            } else if (grid) {
                newSource = "mains"
                textSource = "grid"
            } else if (solar) {
                newSource = "dc"
                textSource = "solar"
            } else if (battPower) {
                newSource =  "battery"  
                textSource = "battery"  
            } else if (gen) {
                newSource =  "generator"  
                textSource = "generator"  
            } else { 
                newSource = "unknown"
                textSource = "unknown"
            }
            
            // +- 50 watts is used as buffer because there is always some of minor draw
            // to or from the grid and batteries
            String batStat = ""
            if ( battCharge < -50 ) { 
                if ( pvPower > 50 ) {    
                    batStat = "Charging Battery from Solar"
                } else if ( genPower > 50 ) {
                    batStat = "Charging Battery from Generator"
                } else {
                    batStat = "Charging Battery from Grid"
                }
            } else if ( battCharge >= -50 && battCharge <= 50 ) { 
                batStat = "Battery not in use"
            } else if ( battCharge > 50 ) {
                if ( gridPower < -50 ) {
                    batStat = "Selling Battery to Grid"
                } else {
                    batStat = "Discharging Battery"  
                }
            } else {
                batStat = "Unknown" 
            }

            //convert all values to kWh and send to device object
            homePower = (homePower / 1000).round(1)
            sendEvent(name: "Load", value: homePower, unit: "kW")
            
            pvPower = (pvPower / 1000).round(1)
            sendEvent(name: "PVPower", value: pvPower, unit: "kW")

            //set presence value to know if the grid is available
            //set before gridpower is converted to kWH for better detection of grid down events
            if ( gridPower == 0 || gridPower == 0.0 ) {
                sendEvent(name: "presence", value: "not present")
            } else {
                sendEvent(name: "presence", value: "present")
            }

            gridPower = (gridPower / 1000).round(1)
            sendEvent(name: "GridPowerDraw", value: gridPower, unit: "kW")

            battCharge = (battCharge / 1000).round(1)
            sendEvent(name: "BatteryDraw", value: battCharge, unit: "kW")

            genPower = (genPower / 1000).round(1)
            sendEvent(name: "GeneratorDraw", value: genPower, unit: "kW")
            
            sendEvent(name: "battery", value:  battSOC, unit: "%")         
            sendEvent(name: "powerSource", value: newSource)
            sendEvent(name: "BatteryStatus", value: batStat)
            
            if (txtEnable) {
                if (batStat == "Battery not in use") { //extra spaces in log to make it easier to read in hubitat log viewer.
                    log.info "    Load is drawing ${homePower}${device.currentState("Load").unit}. Battery is at ${battSOC}%. ${batStat}"
                }
                else {
                    log.info "    Load is drawing ${homePower}${device.currentState("Load").unit}. Battery is at ${battSOC}%, ${batStat} at ${battCharge}${device.currentState("BatteryDraw").unit}."
                }
                log.info "Power Source is ${textSource}, Solar is ${pvPower}${device.currentState("PVPower").unit} ,Grid is ${gridPower}${device.currentState("GridPowerDraw").unit}, Gen is ${genPower}${device.currentState("GeneratorDraw").unit}"
            }

            //Return the value for battery (dis)charging. This is used for later calculations for inverter usage.
            return [battCharge, pvPower, gridPower, genPower]
        })

    } catch (exception) {
        if (logEnable) {
            log.debug exception
        }
        log.error("get flow error. enable debugging for further detail")
        return null
    }
}

void getAmperage(float battCharge, float pvPower, float gridPower, float genPower) {
     def key = "Bearer ${state.xTokenKeyx}"
     def paramsAmps = [  
        uri: "https://api.solarkcloud.com/api/v1/inverter/${state.inverterSN}/realtime/output",
        headers: [ 'Authorization' : key ],
        requestContentType: "application/x-www-form-urlencoded"
        ]
    
        httpGet(paramsAmps, { resp ->
            if (logEnable) log.debug(resp.getData().data)
            try {
                def systemVoltage = (state.systemVoltage).toInteger()
                int inverterACOutput = (resp.getData().data.pac).toInteger()
                if ( systemVoltage >= 240 ) {
                    systemVoltage = systemVoltage
                } else {
                    systemVoltage = 240
                }
                
                def amperes = Math.round(inverterACOutput/systemVoltage)

                if ( state.inverterLimit > 0 ) {
                    def invOutput = Math.round((amperes/state.inverterLimit)*100)
                    def invMsg = "Inverter ${amperes} amps, ${invOutput}% of the limit."
                    def battMsg = ""
                    def battOutput = 0
                    //special limit for 15K model which has 50A limitation on batteries only
                    if ( state.SystemSize == 15000 && Math.abs(battCharge) > 0) {
                        // account for charging battery from multiple sources like PV and Grid. Only grid power is being inverted.
                        if ( battCharge < 0 && Math.abs(battCharge) > pvPower ) {
                            battCharge = Math.abs(battCharge) - pvPower
                            battCharge = ((battCharge*1000)/240).round(2)
                            battOutput = ((battCharge/50)*100).round(2)
                            battMsg = "${battCharge} amps into the battery, ${battOutput}% of battery only limit."
                        } else if ( battCharge < 0 && battCharge <= pvPower ) {
                            battMsg = "${battCharge} amps into the battery, all from solar which does not use the inverter."
                        } else if ( battCharge > 0 ) {
                            battCharge = ((battCharge*1000)/240).round(2)
                            battOutput = ((battCharge/50)*100).round(2)
                            battMsg = "${battCharge} amps from the battery, ${battOutput}% of battery only limit."
                        }
                    }
                    if ( invOutput >= 90 || battOutput >= 90 ) {
                        log.error("${invMsg} ${battMsg}")
                    } else if ( invOutput >= 75 || battOutput >= 75 ) {
                        log.warn("${invMsg} ${battMsg}") 
                    } else {
                        log.info("${invMsg} ${battMsg}") 
                    }
                } else {
                    log.warn("Inverter ${amperes} amps, inverter model and limit is unknown")
                }

                sendEvent(name: "amperage", value: amperes, unit: "A")

            } catch (exception) { 
                if (logEnable) {
                    log.debug exception
                }
                log.error("get amperage details error. enable debugging for further detail")
                return null
            }
        })
}

void getPVDetails() {
     def key = "Bearer ${state.xTokenKeyx}"
     def paramsAmps = [  
        uri: "https://api.solarkcloud.com/api/v1/inverter/${state.inverterSN}/realtime/input",
        headers: [ 'Authorization' : key ],
        requestContentType: "application/x-www-form-urlencoded"
        ]
    
        httpGet(paramsAmps, { resp ->
            if (logEnable) log.debug(resp.getData().data)
            try {
                def PVPowerToday = resp.getData().data.etoday
                def PVPowerAlltime = resp.getData().data.etotal
                
                sendEvent(name: "PVPowerToday", value: PVPowerToday, unit: "kWh")
                sendEvent(name: "PVPowerAlltime", value: PVPowerAlltime, unit: "kWh")

                //Call function for more detailed grid data
                //getGridDetails()

            } catch (exception) { 
                if (logEnable) {
                    log.debug "getPVDetails() - ${exception}"
                }
                log.error("get PV details error. enable debugging for further detail")
                return null
            }
        })           
}

void getGridDetails() {
     def key = "Bearer ${state.xTokenKeyx}"
     def paramsAmps = [  
        uri: "https://api.solarkcloud.com/api/v1/inverter/grid/${state.inverterSN}/realtime",
        headers: [ 'Authorization' : key ],
        requestContentType: "application/x-www-form-urlencoded"
        ]
    
        httpGet(paramsAmps, { resp ->
            if (logEnable) log.debug(resp.getData().data)
            try {
                def GridImportToday = resp.getData().data.etodayFrom
                def GridImportAlltime = resp.getData().data.etotalFrom
                def GridExportToday = resp.getData().data.etodayTo
                def GridExportAlltime = resp.getData().data.etotalTo
                
                sendEvent(name: "GridImportToday", value: GridImportToday, unit: "kWh")
                sendEvent(name: "GridImportAlltime", value: GridImportAlltime, unit: "kWh")
                sendEvent(name: "GridExportToday", value: GridExportToday, unit: "kWh")
                sendEvent(name: "GridExportAlltime", value: GridExportAlltime, unit: "kWh")

                //Call function for more detailed load data
                //getLoadDetails()

            } catch (exception) { 
                if (logEnable) {
                    log.debug "getGridDetails() - ${exception}"
                }
                log.error("get grid details error. enable debugging for further detail")
                return null
            }
        })           
}

void getLoadDetails() {
     def key = "Bearer ${state.xTokenKeyx}"
     def paramsAmps = [  
        uri: "https://api.solarkcloud.com/api/v1/inverter/load/${state.inverterSN}/realtime",
        headers: [ 'Authorization' : key ],
        requestContentType: "application/x-www-form-urlencoded"
        ]
    
        httpGet(paramsAmps, { resp ->
            if (logEnable) log.debug(resp.getData().data)
            try {
                def LoadToday = resp.getData().data.dailyUsed
                def LoadAlltime = resp.getData().data.totalUsed
                
                sendEvent(name: "LoadToday", value: LoadToday, unit: "kWh")
                sendEvent(name: "LoadAlltime", value: LoadAlltime, unit: "kWh")

                //Call function for more detailed battery data
                //getBattDetails()

            } catch (exception) { 
                if (logEnable) {
                    log.debug "getLoadDetails() - ${exception}"
                }
                log.error("get load details error. enable debugging for further detail")
                return null
            }
        })           
}

void getBattDetails() {
     def key = "Bearer ${state.xTokenKeyx}"
     def paramsAmps = [  
        uri: "https://api.solarkcloud.com/api/v1/inverter/battery/${state.inverterSN}/realtime",
        headers: [ 'Authorization' : key ],
        requestContentType: "application/x-www-form-urlencoded"
        ]
    
        httpGet(paramsAmps, { resp ->
            if (logEnable) log.debug(resp.getData().data)
            try {
                def BatteryChargeToday = resp.getData().data.etodayChg
                def BatteryChargeAlltime = resp.getData().data.etotalChg
                def BatteryDischargeToday = resp.getData().data.etodayDischg
                def BatteryDischargeAlltime = resp.getData().data.etotalDischg
                
                sendEvent(name: "BatteryChargeToday", value: BatteryChargeToday, unit: "kWh")
                sendEvent(name: "BatteryChargeAlltime", value: BatteryChargeAlltime, unit: "kWh")
                sendEvent(name: "BatteryDischargeToday", value: BatteryDischargeToday, unit: "kWh")
                sendEvent(name: "BatteryDischargeAlltime", value: BatteryDischargeAlltime, unit: "kWh")

                //Call function for more detailed generator data
                //getGenDetails()

            } catch (exception) { 
                if (logEnable) {
                    log.debug "getBattDetails() - ${exception}"
                }
                log.error("get battery details error. enable debugging for further detail")
                return null
            }
        })           
}

void getGenDetails() {
     def key = "Bearer ${state.xTokenKeyx}"
     def paramsAmps = [  
        uri: "https://api.solarkcloud.com/api/v1/inverter/gen/${state.inverterSN}/realtime",
        headers: [ 'Authorization' : key ],
        requestContentType: "application/x-www-form-urlencoded"
        ]
    
        httpGet(paramsAmps, { resp ->
            if (logEnable) log.debug(resp.getData().data)
            try {
                def GeneratorToday = resp.getData().data.genDaily
                def GeneratorAlltime = resp.getData().data.genTotal
                
                sendEvent(name: "GeneratorToday", value: GeneratorToday, unit: "kWh")
                sendEvent(name: "GeneratorAlltime", value: GeneratorAlltime, unit: "kWh")

            } catch (exception) { 
                if (logEnable) {
                    log.debug "getGenDetails() - ${exception}"
                }
                log.error("get generator details error. enable debugging for further detail")
                return null
            }
        })           
}

void getMonthlyDetails() {
    def key = "Bearer ${state.xTokenKeyx}"
    def month = new Date().format("MM")
    def year = new Date().format("YYYY")
    def months = []
    def years = []
    def loadArray = ["LoadThisMonth", "LoadLastMonth"]
    def PVArray = ["PVThisMonth", "PVLastMonth"]
    def GridExportArray = ["GridExportThisMonth", "GridExportLastMonth"]
    def GridImportArray = ["GridImportThisMonth", "GridImportLastMonth"]
    def BatteryChargeArray = ["BatteryChargeThisMonth", "BatteryChargeLastMonth"]
    def BatteryDischargeArray = ["BatteryDischargeThisMonth", "BatteryDischargeLastMonth"]
    def GeneratorArray = ["GeneratorThisMonth", "GeneratorLastMonth"]

    months.add(month)
    years.add(year)

    month--
    if ( month == "00" ) {
        month = "12"
        year--
    }
    months.add(month)
    years.add(year)

    for ( int i=0; i<=1 ; i++ ) {
        def paramsAmps = [  
        uri: "https://api.solarkcloud.com/api/v1/plant/energy/${plantID}/year/?lan=en&date=" + years[i] + "-" + months[i] + "&id=${plantID}",
        headers: [ 'Authorization' : key ],
        requestContentType: "application/x-www-form-urlencoded"
        ]
    
        httpGet(paramsAmps, { resp ->
            if (logEnable) log.debug(resp.getData().data)
            try {
                resp.getData().data.infos.each {
                    if ( it.label == "Load" ) {
                        sendEvent(name: "${loadArray[i]}", value: Math.round(it.records.value[0].toFloat()), unit: "kWh")
                    } else if ( it.label == "PV" ) {
                        sendEvent(name: "${PVArray[i]}", value: Math.round(it.records.value[0].toFloat()), unit: "kWh")
                    } else if ( it.label == "Export" ) {
                        sendEvent(name: "${GridExportArray[i]}", value: Math.round(it.records.value[0].toFloat()), unit: "kWh")
                    } else if ( it.label == "Import" ) {
                        sendEvent(name: "${GridImportArray[i]}", value: Math.round(it.records.value[0].toFloat()), unit: "kWh")
                    } else if ( it.label == "Charge" ) {
                        sendEvent(name: "${BatteryChargeArray[i]}", value: Math.round(it.records.value[0].toFloat()), unit: "kWh")
                    } else if ( it.label == "Discharge" ) {
                        sendEvent(name: "${BatteryDischargeArray[i]}", value: Math.round(it.records.value[0].toFloat()), unit: "kWh")
                    } else if ( it.label == "Generator" ) {
                        sendEvent(name: "${GeneratorArray[i]}", value: Math.round(it.records.value[0].toFloat()), unit: "kWh")
                    }
                }
            } catch (exception) { 
                if (logEnable) {
                    log.debug "getMonthlyDetails() - ${exception}"
                }
                log.error("get monthly details error. enable debugging for further detail")
                return null
            }
        })
    } 
}

void updateTiles() {

    try {
        //color battery based on the SOC
        if ( device.currentValue("battery") >= 80 ) {
            battColor = "style='color:green;'>"
        } else if ( device.currentValue("battery") < 40 ) {
            battColor = "style='color:red;'>"
        } else {
            battColor = "style='color:yellow;'>"
        }

        //when grid is down, highlight it in red
        if ( device.currentValue("presence") == "not present" ) {
            gridColor = "style='color:red;'>"
        } else {
            gridColor = ">"
        }

        //bold the names of the sources of power. this could be multiple like solar and grid.
        if ( device.currentState("PVPower").unit == "kW" && device.currentValue("PVPower") > 0 ) {
            pvStyle = "style='text-align:left; font-weight:bold;'>"
        } else {
            pvStyle = "style='text-align:left;'>"
        }

        if ( device.currentState("GridPowerDraw").unit == "kW" && device.currentValue("GridPowerDraw") > 0 ) {
            gridStyle = "style='text-align:left; font-weight:bold;'>"
        } else {
            gridStyle = "style='text-align:left;'>"
        }
        
        if ( device.currentState("GeneratorDraw").unit == "kW" && device.currentValue("GeneratorDraw") > 0 ) {
            genStyle = "style='text-align:left; font-weight:bold;'>"
        } else {
            genStyle = "style='text-align:left;'>"
        }

        if ( device.currentState("BatteryDraw").unit == "kW" && device.currentValue("BatteryDraw") > 0 ) {
            battStyle = "style='text-align:left; font-weight:bold;'>"
        } else {
            battStyle = "style='text-align:left;'>"
        }

        def compTile = "<table class='solarTable' cellspacing='0' cellpadding='0'>"
            compTile += "<tr><td colspan=2 style='text-align:center;'>RealTime</td><td style='text-align:center;'>Today</td></tr>"
            compTile += "<tr><td style='text-align:left;'>Load:</td><td>" + device.currentValue("Load").toString() + device.currentState("Load").unit + "</td><td>" + Math.round(device.currentValue("LoadToday")).toString() + " kWh</td></tr>"
            compTile += "<tr><td " + pvStyle + "Sol:</td><td>" + device.currentValue("PVPower").toString() + device.currentState("PVPower").unit + "</td><td>" + Math.round(device.currentValue("PVPowerToday")).toString() + " kWh</td></tr>"
            compTile += "<tr><td " + gridStyle + "Grid:</td><td class='solarSmall' " + gridColor + device.currentValue("GridPowerDraw").toString() + device.currentState("GridPowerDraw").unit + "</td><td class='solarSmall' style='text-align:center;'>" + Math.round(device.currentValue("GridImportToday")).toString() + " kWh I / </td><td class='solarSmall' style='text-align:center;'>" + Math.round(device.currentValue("GridExportToday")).toString() + " kWh E</td></tr>"
            compTile += "<tr><td " + genStyle + "Gen:</td><td>" + device.currentValue("GeneratorDraw").toString() + device.currentState("GeneratorDraw").unit + "</td><td>" + Math.round(device.currentValue("GeneratorToday")).toString() + " kWh</td></tr>"
            compTile += "<tr><td " + battStyle + "Bat:</td><td class='solarSmall'>" + device.currentValue("BatteryDraw").toString() + device.currentState("BatteryDraw").unit + "</td><td class='solarSmall' style='text-align:center;'>" + Math.round(device.currentValue("BatteryChargeToday")).toString() + " kWh C / </td><td class='solarSmall' style='text-align:center;'>" + Math.round(device.currentValue("BatteryDischargeToday")).toString() + " kWh D</td></tr>"
            compTile += "<tr><td style='text-align:left;'>Bat:</td><td " + battColor + device.currentValue("battery").toString() + " %</td>"
            compTile += "<td class='solarDate' colspan=3 style='text-align:center;'>" + new Date().format("MM-dd HH:mm") + "</td></tr>"
            compTile += "</table>"

        if (logEnable) log.debug ("comp tile:" + compTile.length() +  " ${compTile}")
        if (compTile.length() >= 1024) log.error ("compTile exceeding 1024 charcters. Will not display on dashboard correctly.")
        sendEvent(name: "ComprehensiveTile", value: compTile)

        def monthTile = "<table class='solarTable' cellspacing='0' cellpadding='0'>"
            monthTile += "<tr><td colspan=2 style='text-align:center;'>Last Month</td><td colspan=1 style='text-align:center;'>This Month</td></tr>"
            monthTile += "<tr><td style='text-align:left;'>Load:</td><td>" + device.currentValue("LoadLastMonth").toString() +  " kWh</td><td>" + device.currentValue("LoadThisMonth").toString() + " kWh</td></tr>"
            monthTile += "<tr><td style='text-align:left;'>Sol:</td><td>" + device.currentValue("PVLastMonth").toString()  + " kWh</td><td>" + device.currentValue("PVThisMonth").toString() + " kWh</td></tr>"
            monthTile += "<tr><td style='text-align:left;'>Grid:</td><td class='solarSmall'>" + device.currentValue("GridImportLastMonth").toString() + " I/ " + device.currentValue("GridExportLastMonth").toString() + " E </td><td class='solarSmall'>" + device.currentValue("GridImportThisMonth").toString() + " I/ " + device.currentValue("GridExportThisMonth").toString() + " E</td></tr>"
            monthTile += "<tr><td style='text-align:left;'>Gen:</td><td>" + device.currentValue("GeneratorLastMonth").toString() + " kWh</td><td>" + device.currentValue("GeneratorLastMonth").toString() + " kWh</td></tr>"
            monthTile += "<tr><td style='text-align:left;'>Bat:</td><td class='solarSmall'>" + device.currentValue("BatteryChargeLastMonth").toString() + " C/ " + device.currentValue("BatteryDischargeLastMonth").toString() + " D</td><td class='solarSmall'>" + device.currentValue("BatteryChargeThisMonth").toString() + " C/ " + device.currentValue("BatteryDischargeThisMonth").toString() + " D</td></tr>"
            monthTile += "<tr><td class='solarDate' colspan=3 style='text-align:center;'>" + new Date().format("MM-dd HH:mm") + "</td></tr>"
            monthTile += "</table>"
            
        if (logEnable) log.debug ("monthly tile:" + monthTile.length() + " ${monthTile}")
        if (monthTile.length() >= 1024) log.error ("monthTile exceeding 1024 charcters. Will not display on dashboard correctly.")
        sendEvent(name: "MonthlyTile", value: monthTile)
    } catch (exception) { 
        if (logEnable) {
            log.debug "updateTiles() - ${exception}"
        }
        log.error("updateTiles error. enable debugging for further detail")
        return null
    }
}