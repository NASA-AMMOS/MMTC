#!/bin/bash

set -ex

#grant g+w to conf directory so that mmtc group members can update or create new config files
#grant g+w to plugins directory so that mmtc group members can add new plugins
#grant g+w to log directory so MMTC can write new log files when run by mmtc group members
chmod g+w -R $RPM_INSTALL_PREFIX/conf $RPM_INSTALL_PREFIX/lib/plugins $RPM_INSTALL_PREFIX/log $RPM_INSTALL_PREFIX/output
