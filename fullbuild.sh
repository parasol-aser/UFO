#!/bin/sh

RED='\033[0;31m'
NC='\033[0m'

# comment this when you have modified args.gn
echo "First, please modify args.gn to to set ${RED}clang_base_path = {UFO_DIR}/build${NC}"; exit;

UFO_HOME=$PWD

# install git and cmake
sudo apt install git cmake

# build LLVM+UFO
export BRANCH=release_70
git clone http://llvm.org/git/llvm.git -b $BRANCH
git clone http://llvm.org/git/clang.git llvm/tools/clang -b $BRANCH
git clone http://llvm.org/git/clang-tools-extra.git llvm/tools/clang/tools/extra -b $BRANCH
mv ufo-rt llvm/projects/
mkdir build && cd build
cmake -G "Unix Makefiles" -DCMAKE_BUILD_TYPE=Release -DLLVM_BUILD_TESTS=OFF -DLLVM_INCLUDE_TESTS=OFF -DLLVM_BUILD_EXAMPLES=OFF -DLLVM_INCLUDE_EXAMPLES=OFF -DLLVM_ENABLE_ASSERTIONS=OFF ../llvm
make -j8
export PATH=$UFO_HOME/build/bin:$PATH

# get src for chromium 70.0.3537.2 (last build to use clang 7.0.0)
cd $UFO_HOME && mkdir chromium && cd chromium
git clone https://chromium.googlesource.com/chromium/tools/depot_tools.git
export PATH=$UFO_HOME/chromium/depot_tools:$PATH
fetch --nohooks chromium
cd src && ./build/install-build-deps.sh
git checkout tags/70.0.3537.2 -b v70.0.3537.2
# NOTE: if gclient sync terminates bc of error 57 (unstaged changes), you should
# follow the steps found in this thread:
# https://groups.google.com/a/chromium.org/forum/#!topic/chromium-dev/SgwR4kEMZ7I
# Then, you can run the commands STARTING from gclient sync
gclient sync
gn gen out/ufo
mv $UFO_HOME/args.gn ./out/ufo/
mv $UFO_HOME/BUILD.gn ./build/config/compiler/
# alternatively, instead of adding to the tail of $PATH, we could copy chromium's
# lld and its symbolic links into our ufo/build/bin directory. You should change
# to the alternative if you have lld defined already in your path somewhere.
export PATH=$PATH:$UFO_HOME/chromium/src/third_party/llvm-build/Release+Asserts/bin/

# build chromium
autoninja -C out/ufo chrome
