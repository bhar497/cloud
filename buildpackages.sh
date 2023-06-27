#!/bin/bash

DEBEMAIL='nlgordon@gmail.com' dch --local "+local" "local build"
dpkg-buildpackage -uc -us
cd ..
rm /var/www/html/cs-packages/binaries/*.deb
mv *.deb /var/www/html/cs-packages/binaries
rm cloudstack_*
cd /var/www/html/cs-packages/
./updatePackages.sh

