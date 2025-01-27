# Hubitat
Forked repo from pentalingual. Only made changes to the Solark Inverter driver. 
## Introduction
SolArk and SunSynk Solar inverters use the MySolark app for reporting and monitoring. This driver allows you to set a refresh schedule to pull the data from the MySolark API. It maintains the current Inverter states as a device in Hubitat. These are based on solar production, grid usage, generator usage, battery charge, and grid presence. They can be used to trigger other automations like alerting when the grid goes down or when your battery SOC is too low.
