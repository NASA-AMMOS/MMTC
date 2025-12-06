# MMTC: Multi-Mission Time Correlation

The Multi-Mission Time Correlation (MMTC) application is ground software which provides an automated means of correlating spacecraft time to ground time, along with associated functionality. MMTC is designed to be adaptable to any spacecraft and ground system, and is intended to be included in space mission ground data systems and to be run in a Mission Operations Center (MOC) or Mission Support Area (MSA.)  It produces time correlation products in standard, widely-used formats, including [NAIF SPICE](https://naif.jpl.nasa.gov/naif/) SCLK kernels.

MMTC is licensed under the Apache License 2.0.  See LICENSE.md for a copy of the license terms.

## Introduction

### Context

Clocks on board spacecraft, like all clocks, experience 'drift', meaning the period between their 'ticks' is not exactly once per second (and in fact can vary in the face of external factors, such as temperature, voltage, current, relativistic effects, etc.)

Most space missions have requirements which demand a means of maintaining an accurate, and in many cases, extremely accurate knowledge of time as measured onboard a spacecraft. For deep-space missions, this is typically performed through so-called time correlation, which is typically accomplished via a combination of spacecraft radio(s), avionics, and one or more oscillators in combination with ground antenna and ground data systems.

The decades-long history of space missions performing time correlation, along with the common principles that underlie many of their time correlation and timekeeping operations, provide the motivation for a common & multi-mission time correlation capability.

### Overview

MMTC accomplishes its primary function (time correlation) by analyzing spacecraft telemetry and establishing a relationship between spacecraft clock (SCLK) time and Earth Receive Time (ERT) while accounting for several complicating factors (such as one-way light time.) MMTC also offers configurable 'filters' for ensuring the quality of data used for time correlation meets mission requirements. The primary result of this correlation is an updated Clock Change Rate, which encodes the rate of an Earth clock's 'ticks' to the SCLK's 'ticks.' This information - a related pair of an SCLK value and the ground time, as well as the measured Clock Change Rate - serves as a new correlation record and is written to new SCLK kernels and/or SCLK/SCET files.

MMTC creates the following output products:
- SCLK kernels, compatible with the NAIF SPICE system of software and data
- SCLK/SCET files, compatible with various JPL and AMMOS software
- A number of types of CSV files, containing ancillary telemetry and data useful for analysis and/or anomaly resolution, and data that may be used to update onboard Guidance, Navigation and Control parameters

MMTC contains additional functionality, including:
- the ability to read and write telemetry from and to plaintext CSV files - the ability to 'roll back' the state of output products to prior runs

Missions adapt MMTC to their mission by accomplishing the following two steps:
1. Creating an MMTC configuration that defines relevant details about their spacecraft, its telemetry, and the mission's ground data system
2. Connecting MMTC to their mission's telemetry by selecting an existing MMTC Telemetry Source implementation or implementing a new Telemetry Source plugin using the included SDK
   MMTC is implemented in Java and relies on NAIF SPICE software and data. MMTC can be run standalone or integrated into a ground system. MMTC software includes an optional first-party integration with the AMMOS commanding and telemetry system, AMPCS, and can be extended to integrate with additional ground systems via its plugin interface.

## Quick Start

Build requirements:
- Red Hat Enterprise Linux (RHEL) 8 or 9 on an x86-64 host
- Java 17

Runtime requirements:
- Red Hat Enterprise Linux (RHEL) 8 or 9 on an x86-64 host
- Java 8 for all components except the web application, which requires Java 17

After cloning the repository and running `./gradlew build`, two MMTC installation options are available:

### Demo

For users who wish to experiment with MMTC’s behavior and functionality without configuring it for a specific mission, a turnkey demo is available in the form of a portable installation that comes packaged with example telemetry and configuration from the New Horizons spacecraft in a single .zip file.  To try it out:

1. Generate the compressed demo files with `./gradlew demoZip`
2. Extract the contents of the created .zip file (written within `/build/distributions/`) with `unzip build/distributions/mmtc-[version]-demo.zip -d [path to extract to]`
3. Run `setup-demo-zip.sh` to configure environment variables, adjust the config, and automatically run MMTC several times

See the "Quick Start Guide" section of the User Guide for complete instructions.

### Installation

To create a traditional clean installation of MMTC that is ready for configuration and adaptation:
1. Build the RPM with `./gradlew mmtcEl8Rpm` (or `mmtcEl9Rpm`, as desired)
2. Install RPM (contents are written to /opt/local/mmtc by default)
3. Configure as necessary (see the User Guide)

## Further information

For further information, please see the User Guide at `docs/User_Guide.adoc`, which may be rendered to PDF via `./gradlew :userGuidePdf`, or downloaded from the 'Releases' area.

## Copyright

© 2025 The Johns Hopkins University Applied Physics Laboratory LLC

This work was performed for the Jet Propulsion Laboratory, California Institute of Technology, sponsored by the United States Government under the Prime Contract 80NM0018D00004 between the Caltech and NASA under subcontract number 1658085.