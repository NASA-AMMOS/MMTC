# MMTC Web App

## Goal

Extend the MMTC software to support additional concepts of operations to current and future mission customers by delivering a web application with the following capabilities:

- Manual selection and review of time correlation data, including:
    - Plotting of SCLK/ERT data, including SCLK-SCET Error and Ep(ms) metrics, sourced from a mission’s telemetry archive
    - Allowing selection of target packet/frame data for a new correlation point, previewing the correlation results, and executing the correlation
- Providing for the display and retrieval of SCLK kernels, SCLK-SCET files, and other existing MMTC outputs
- Allow users to ‘roll back’ a correlation (to revert output products to a prior state)
- Serving a basic HTTP API to enable external services/applications to automate correlation & retrieval of output products
- Allowing extension points for integrating methods of authentication
- Webapp should be able to consider users as having read-only or read/write access

## Design

### Usage

- For single-user mode
    - Have a new script `bin/mmtc_app`:
        -  start a Java webserver bound to a random high port on localhost (127.0.0.1), protected by a passcode generated on startup
        - open the default system browser to the URL with the passphrase encoded (a la Jupyter Notebook)
    - start script should check, before startup, that it has r/w permissions to the MMTC output directory
    - webapp should be able to consider users as having read-only or read/write access
    - The single-user mode should require specifying whether it's starting in read/write or read-only mode
- Multi-user mode
    - Deploy the same application as a multi-user service (service file included in distribution)
    - Still need to determine authentication extension points and configuring users' permissions (read/write vs read-only)

### Impl design

- Backend
  - Java 17
  - Javalin
  - Backend logic exposed via REST controllers
  - Frontend built into backend, served via the backend
  - Built to runnable JAR
- Frontend
  - Vue 3, with:
    - A component library, either:
        - [Vuetify](https://vuetifyjs.com/en/)
        - [PrimeVue](https://primevue.org)
        - [Naive UI](https://www.naiveui.com)
      - [Apache Echarts](https://echarts.apache.org/en/index.html) via [Vue-ECharts](https://vue-echarts.dev/)
- Make sure UI can be used with all optional telemetry source options (like extra args to AMPCS)


Gameplan:
- Draw up slide deck with:
  - Overview of MMTC 1.6.0 release plan
  - Overview of MMTC UI task
  - Proposed MMTC UI conops, describing various workflows (using local Penpot)
  - Proposted MMTC UI deployment and invocation
- Email it out a week ahead of a meeting invite to discuss with stakeholders
