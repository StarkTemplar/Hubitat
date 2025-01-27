# Solark Inverter
## Introduction
SolArk and SunSynk Solar inverters use the MySolark app for reporting and monitoring. This driver allows you to set a refresh schedule to pull the data from the MySolark API. It maintains the current Inverter states as a device in Hubitat. These are based on solar production, grid usage, generator usage, battery charge, and grid presence. They can be used to trigger other automations like alerting when the grid goes down or when your battery SOC is too low.
## Supported Models
Currently supported for the following devices. Open an issue to add other devices.
- 12K
- 15K
- 30K
- 60K
## Installation
This driver can be installed using the HPM (Hubitat Package Manager). This allows for easier installation and regular updates. [https://community.hubitat.com/t/release-hubitat-package-manager-hpm-hubitatcommunity/94471](https://community.hubitat.com/t/release-hubitat-package-manager-hpm-hubitatcommunity/94471)\
Using HPM, you can search for the name of this driver "SolArk Inverter". There may be multiple authors and versions so reference this repository name and version for the correct item in the list.
## Configuration
Once you have the driver installed, you have to create a new virtual device, and use this driver. More details can be found on the hubitat forums. [https://community.hubitat.com/t/solark-sunsynk-inverter-api-link/125050/15](https://community.hubitat.com/t/solark-sunsynk-inverter-api-link/125050/15)\
On the preferences page, you have to provide the plant ID, username, and password for your MySolArk account. The plant ID you can find in the URL when you view your plant from the web.
## Hubitat Dashboard
There are also 2 output tiles to make for easier dashboard creation. On a hubitat dashboard, view the attribute "ComprehensiveTile" or "MonthlyTile" to see the data in an easy to read format.\
![image of comprehensive and monthly tile](https://community.hubitat.com/uploads/default/original/3X/7/d/7d0496adeaa8f589c9687d4299b6528e391db885.png)\
I also modify the spacing and sizing using the hubitat CSS editor to make it more readable and fit in a smaller tile.

```
.solarTable{
padding: 1px;
border-spacing: 1px;
}
.solarDate, .solarSmall{
font-size: 80% !important;
}
```
<br>There are many other attributes available that are saved to the state of this device. These can be viewed individually or you can request a different tile output and I can add them to a new tile.

```
"lastresponsetime"
"PVPower"
"PVPowerToday"
"PVLastMonth"
"PVThisMonth"
"PVPowerAlltime"
"GridPowerDraw"
"GridImportToday"
"GridImportLastMonth"
"GridImportThisMonth"
"GridImportAlltime"
"GridExportToday"
"GridExportLastMonth"
"GridExportThisMonth"
"GridExportAlltime"
"Load"
"LoadToday"
"LoadLastMonth"
"LoadThisMonth"
"LoadAlltime"
"battery"
"BatteryDraw"
"BatteryStatus"
"BatteryChargeToday"
"BatteryChargeLastMonth"
"BatteryChargeThisMonth"
"BatteryChargeAlltime"
"BatteryDischargeToday"
"BatteryDischargeLastMonth"
"BatteryDischargeThisMonth"
"BatteryDischargeAlltime"
"GeneratorDraw"
"GeneratorToday"
"GeneratorLastMonth"
"GeneratorThisMonth"
"GeneratorAlltime"
"ComprehensiveTile"
"MonthlyTile"
"presence"
```

## Roadmap
- Complete testing on generator data.
- Complete testing on inverter limit monitoring.
- Provide option for users with AC solar connections. I need a user configured like this so that I can see how this data is presented through the API.
- Provide more output tiles.