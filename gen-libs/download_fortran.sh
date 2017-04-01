#!/bin/sh
DIR=$(dirname $(readlink -f $0))
RELEASES_URL="https://github.com/buffer51/android-gfortran/releases/download/r13b"
TOOLCHAINS="gcc-arm-linux-x86_64 \
  gcc-arm64-linux-x86_64 \
  gcc-mips-linux-x86_64 \
  gcc-mips64-linux-x86_64 \
  gcc-x86-linux-x86_64 \
  gcc-x86_64-linux-x86_64"

WD=$DIR/src/main/cpp/openblas/toolchains
mkdir -p $WD
cd $WD
for t in $TOOLCHAINS ; do
  wget ${RELEASES_URL}/${t}.tar.bz2
  tar xvfj ${t}.tar.bz2
done

