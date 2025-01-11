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
 */

static String version() { return '0.4.6' }

metadata {
    definition(
            name: "SolArk Inverter",
            namespace: "pentalingual",
            author: "Andrew Nunes",
            description: "Leverages the MySolArk API to update Hubitat with your SolArk inverter status",
            category: "Integrations",
            importUrl: "https://raw.githubusercontent.com/pentalingual/Hubitat/main/Solar/SolArk_Inverter.groovy"
    )  {
        capability "Initialize"
        capability "Refresh"
        capability "PowerMeter"
        capability "PowerSource"
        capability "CurrentMeter"
        capability "Battery" 

        attribute "lastresponsetime", "string"
        attribute "PVPower", "number"
        attribute "PVPowerToday", "number"
        attribute "PVPowerAlltime", "number"
        attribute "GridPowerDraw", "number"
        attribute "GridImportToday", "number"
        attribute "GridImportAlltime", "number"
        attribute "GridExportToday", "number"
        attribute "GridExportAlltime", "number"
        attribute "Load", "number"
        attribute "LoadToday", "number"
        attribute "LoadAlltime", "number"
        attribute "battery", "number"
        attribute "BatteryDraw", "number"
        attribute "BatteryStatus", "string"
        attribute "BatteryChargeToday", "number"
        attribute "BatteryChargeAlltime", "number"
        attribute "BatteryDischargeToday", "number"
        attribute "BatteryDischargeAlltime", "number"
        attribute "GeneratorDraw", "number"
        attribute "GeneratorToday", "number"
        attribute "GeneratorAlltime", "number"
        attribute "ComprehensiveTile", "string"
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
     getToken(true)
     runIn(10,refresh)
     schedule("0 0/${refreshSched} * * * ?", refresh)
     log.info "Refreshing every ${refreshSched} minutes. Debug logging is: ${logEnable}."
}
                  
                  
def updated() {
    log.info "Preferences saved."
    initialize()
}

def refresh() {
    
    try{
        if ( getPlantDetails() ) {
            def AbsBatt = queryData()
            getAmperage(AbsBatt)
            getPVDetails()
            getGridDetails()
            getLoadDetails()
            getBattDetails()
            getGenDetails()
            updateTiles()
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

void getToken(refreshToken) {
    if ( refreshToken == true ) {
        body1 = ['username':Username,'password':Password,'grant_type':'password','client_id':'csp-web','source':'elinter']    
    } else {
        body1 = ['username':Username,'password':Password,'grant_type':'refresh_token','refresh_token':state.xTokenRefreshKeyx,'client_id':'csp-web','source':'elinter']
    }
    // def URIa = "https://openapi.inteless.com/v1/oauth/token"
    // def URIb = "https://pv.inteless.com/api/v1/oauth/token"
    def URIc = "https://www.solarkcloud.com/oauth/token"
    def paramsTOK = [
        uri: URIc,
        headers: [
            'Origin': 'https://www.solarkcloud.com/',
            'Referer': 'https://www.solarkcloud.com/login'
        ],
        body: body1
    ]
    //if (logEnable) log.debug("body1: ${body1}")
    //* Catch error and define. 443. 401, read timed out
    try {
        httpPostJson(paramsTOK, { resp -> 
            if (logEnable) log.debug(resp.getData().data)
            
            state.xTokenKeyx = resp.getData().data.access_token
            state.xTokenRefreshKeyx = resp.getData().data.refresh_token
            def tokenExpiration = resp.getData().data.expires_in as Integer
            if (logEnable) log.debug("token expiration: ${tokenExpiration}")

            def tokenRefreshJob = tokenExpiration - ((refreshSched.toInteger() * 60) / 2) as Integer
            if (logEnable) log.debug("schedule to get new token in ${tokenRefreshJob} seconds")
            runIn(tokenRefreshJob,getToken)
        })
    } catch (Exception) {
        if (logEnable) {
            log.debug exception
        }
        log.error "unable to login. This could be due to an invalid username/password, or MySolArk site may be down. enable debugging for further detail."
    }
}

void getToken() {
    //if function receives no parameter, call getToken with a parameter = false
    getToken(false)
}


def getPlantDetails() {
    def key = "Bearer ${state.xTokenKeyx}"
    body5 = ['status': 1, 'type': -1, 'limit': 1, 'page': 1]
    def paramsInitial = [  
        uri: "https://www.solarkcloud.com/api/v1/plant/${plantID}/inverters",
        headers: [ 'Authorization' : key], 
        query: body5
        ]
       try { 
        httpGet(paramsInitial,  { resp ->
            if (logEnable) log.debug(resp.getData().data)
            
            def systemModel = resp.getData().data.infos.ratePower[0]
            state.SystemSize =  systemModel.toInteger()
            state.inverterSN = resp.getData().data.infos.sn[0]

            /* inverter's AC Limit is different depending on models
                * 12K - 37.5A
                * 15K - 62.5A total, 50A batteries only
                * 30K - 83.4A
                * 60K - 72.3A
            */
            if ( state.SystemSize == 12000 ) {
                state.inverterLimit = 37
            } else if ( state.SystemSize == 15000 ) {
                state.inverterLimit = 62
            } else if ( state.SystemSize == 30000 ) {
                state.inverterLimit = 83
            } else if ( state.SystemSize == 60000 ) {
                state.inverterLimit = 72
            } else {
                state.inverterLimit = 0
            }

            return true
        })
        }
    
     catch (groovyx.net.http.HttpResponseException exception) {
        if (logEnable) {
            log.debug exception
        }
        if ( exception.getStatusCode() == 401 ) {
            log.error("http 401 - token has expired. trying to refresh token")
            if ( state.xTokenRefreshKeyx ) {
                getToken()
            }
        } else {
            log.error("unable to return the inverter for this plant you may need to check that the plant id ${plantID} is correct. enable debugging for further detail.")
        }
        return null
     } catch (exception) {
        if (logEnable) {
            log.debug exception
        }
        log.error("unable to return the inverter for this plant you may need to check that the plant id ${plantID} is correct. enable debugging for further detail.")
        return null
    }
}


def queryData()  {
    def key = "Bearer ${state.xTokenKeyx}"
    def paramsEnergy = [  
        uri: "https://www.solarkcloud.com/api/v1/plant/energy/${plantID}/flow",
        headers: [ 'Authorization' : key ],
        requestContentType: "application/x-www-form-urlencoded"
    ]
    try {
        httpGet(paramsEnergy, { resp ->
            if (logEnable) log.debug(resp.getData().data)
            
            boolean grid = resp.getData().data.gridOrMeterPower > 120
            boolean solar = resp.getData().data.pvPower > 120
            boolean battPower = resp.getData().data.battPower > 120
            boolean gen = resp.getData().data.genPower > 120
            
            float curr = ((resp.getData().data.loadOrEpsPower - resp.getData().data.gridOrMeterPower)/ 120 )
            float amperesEst = curr.round(2)
            
            int homePower = resp.getData().data.loadOrEpsPower
            int gridPower = resp.getData().data.gridOrMeterPower
            if (resp.getData().data.toGrid) gridPower = -gridPower
            
            int pvPower = resp.getData().data.pvPower
            int genPower = resp.getData().data.genPower

            int battSOC = resp.getData().data.soc
            int battCharge = resp.getData().data.battPower
            if (resp.getData().data.toBat) battCharge = battCharge* -1 
            
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
            }else { 
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

            if ( homePower == 0 )   {          
            } else {
                sendEvent(name: "Load", value: homePower, unit: "W")
                sendEvent(name: "battery", value:  battSOC, unit: "%")         
            
                sendEvent(name: "powerSource", value: newSource)
            
                sendEvent(name: "PVPower", value: pvPower, unit: "W")
                sendEvent(name: "GridPowerDraw", value: gridPower, unit: "W")
                sendEvent(name: "BatteryDraw", value: battCharge, unit: "W")
                sendEvent(name: "GeneratorDraw", value: genPower, unit: "W")
                sendEvent(name: "BatteryStatus", value: batStat)
            }
            
            if (txtEnable) {
                if (batStat == "Battery not in use") {
                    if(newSource && battCharge && gridPower) {
                        log.info "Power Source is ${textSource}, Load is drawing ${gridPower} watts. Battery is at a ${battSOC}% charge."
                    } else {
                        log.error "MySolArk API is offline. We will try again in ${refreshSched} minutes."
                    }
                } else { 
                    int AbsBatt = Math.abs(battCharge)
                    if ( AbsBatt < 250) {
                        log.info "Power Source is ${textSource}, Load is drawing ${homePower} watts. Battery is at a ${battSOC}% charge, operating at ${AbsBatt} watts."
                    } else {
                        log.info "Power Source is ${textSource}, ${batStat} at a rate of ${AbsBatt} watts. Battery is at a ${battSOC}% charge."
                    }
                }
            }

            //Call function for more detailed inverter data and warnings
            //getAmperage(Math.abs(battCharge))
            return Math.abs(battCharge)
        })

    } catch (exception) {
        if (logEnable) {
            log.debug exception
        }
        log.error("get flow error. enable debugging for further detail")
        return null
    }
}


void getAmperage(float AbsBatt) {
     def key = "Bearer ${state.xTokenKeyx}"
     def paramsAmps = [  
        uri: "https://www.solarkcloud.com/api/v1/inverter/${state.inverterSN}/realtime/output",
        headers: [ 'Authorization' : key ],
        requestContentType: "application/x-www-form-urlencoded"
        ]
    
        httpGet(paramsAmps, { resp ->
            if (logEnable) log.debug(resp.getData().data)
            try {
                float valVac1 = 0
                def vac1 = resp.getData().data.vip[0]
                if( vac1 ) {
                    vac1 = vac1.current
                    valVac1 = vac1.toFloat()
                                } else {
                     valVac1 = 0
                    }

                float valVac2 = 0
                def vac2 = resp.getData().data.vip[1]
                if( vac2 ) {
                    vac2 = vac2.current
                    valVac2 = vac2.toFloat()
                                } else {
                     valVac2 = 0
                    }
            
                float valVac3 = 0
                def vac3 = resp.getData().data.vip[2]
                if( vac3 ) {
                    vac3 = vac3.current
                    valVac3 = vac3.toFloat()
                } else {
                     valVac3 = 0
                    }

                float amperes = (valVac1 + valVac2 + valVac3).round(2)

                if ( state.inverterLimit > 0 ) {
                    float invOutput = ((amperes/state.inverterLimit)*100).round(2)
                    def invMsg = "Inverter pushing ${amperes} amps, ${invOutput}% of the inverter limit."
                    def battMsg = ""
                    def battOutput = 0
                    //special limit for 15K model which has 50A limitation on batteries only
                    if ( state.SystemSize == 15000 && AbsBatt > 0) {
                        AbsBatt = (AbsBatt/240).round(2)
                        battOutput = ((AbsBatt/50)*100).round(2)
                        battMsg = "${AbsBatt} amps from the battery, ${battOutput}% of the battery only inverter limit."
                    }
                    if ( invOutput >= 90 || battOutput >= 90 ) {
                        log.error("${invMsg} ${battMsg}")
                    } else if ( invOutput >= 75 || battOutput >= 75 ) {
                        log.warn("${invMsg} ${battMsg}") 
                    } else {
                        log.info("${invMsg} ${battMsg}") 
                    }
                } else {
                    log.warn("Inverter pushing ${amperes} amps, inverter model and limit is unknown")
                }

                sendEvent(name: "amperage", value: amperes, unit: "A")

                //Call function for more detailed pv data
                //getPVDetails()

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
        uri: "https://www.solarkcloud.com/api/v1/inverter/${state.inverterSN}/realtime/input",
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
        uri: "https://www.solarkcloud.com/api/v1/inverter/grid/${state.inverterSN}/realtime",
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
        uri: "https://www.solarkcloud.com/api/v1/inverter/load/${state.inverterSN}/realtime",
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
        uri: "https://www.solarkcloud.com/api/v1/inverter/battery/${state.inverterSN}/realtime",
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
        uri: "https://www.solarkcloud.com/api/v1/inverter/gen/${state.inverterSN}/realtime",
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

void updateTiles() {

    if ( device.currentValue("battery") >= 80 ) {
        battColor = "style='color: green;'>"
    } else if ( device.currentValue("battery") < 40 ) {
        battColor = "style='color: red;'>"
    } else {
        battColor = "style='color: yellow;'>"
    }

    if ( device.currentValue("GridPowerDraw") == 0 ) {
        gridColor = "style='color: red;'>"
    } else {
        gridColor = ">"
    }

    def compTile = "<table class='solarTable' cellspacing='0' cellpadding='0'>"
        compTile += "<tr><td colspan=2 style='text-align: center;'>RealTime</td><td colspan=1 style='text-align: center;'>Today</td></tr>"
        compTile += "<tr><td style='text-align: left;'>Loads:</td><td>" + device.currentValue("Load").toString() + "w</td><td>" + device.currentValue("LoadToday").toString() + " kWh</td></tr>"
        compTile += "<tr><td style='text-align: left;'>Solar:</td><td>" + device.currentValue("PVPower").toString() + "w</td><td>" + device.currentValue("PVPowerToday").toString() + " kWh</td></tr>"
        compTile += "<tr><td style='text-align: left;'>Grid:</td><td " + gridColor + device.currentValue("GridPowerDraw").toString() + "w</td><td class='solarSmall'>" + device.currentValue("GridImportToday").toString() + " kWh I / </td><td class='solarSmall'>" + device.currentValue("GridExportToday").toString() + " kWh E</td></tr>"
        compTile += "<tr><td style='text-align: left;'>Gen:</td><td>" + device.currentValue("GeneratorDraw").toString() + "w</td><td>" + device.currentValue("GeneratorToday").toString() + " kWh</td></tr>"
        compTile += "<tr><td style='text-align: left;'>Battery:</td><td>" + device.currentValue("BatteryDraw").toString() + "w</td><td class='solarSmall'>" + device.currentValue("BatteryChargeToday").toString() + " kWh C / </td><td class='solarSmall'>" + device.currentValue("BatteryDischargeToday").toString() + " kWh D</td></tr>"
        compTile += "<tr><td style='text-align: left;'>Battery:</td><td " + battColor + device.currentValue("battery").toString() + " %</td>"
        compTile += "<td class='solarDate' colspan=3 style='text-align: center;'>" + new Date().format("YYYY-MM-dd HH:mm:ss") + "</td></tr>"
        compTile += "</table>"

    if (debug) log.debug "${compTile}"
    sendEvent(name: "ComprehensiveTile", value: compTile)
}