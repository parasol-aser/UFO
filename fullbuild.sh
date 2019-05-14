#!/bin/sh

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
export PATH=$PWD/bin:$PATH

# get src for chromium 70.0.3537.0 (last build to use clang 7.0.0)
cd .. && mkdir chromium && cd chromium
git clone https://chromium.googlesource.com/chromium/tools/depot_tools.git
export PATH=$PWD/depot_tools:$PATH
fetch --nohooks chromium
cd src && sudo ./build/install-build-deps.sh
git checkout tags/70.0.3537.0 -b v70.0.3537.0
gclient sync
gn gen out/ufo
mv ../../args.gn ./out/ufo/
mv ../../BUILD.gn ./build/config/compiler/
# alternatively, instead of adding to the tail of $PATH, we could copy chromium's
# lld and its symbolic links into our ufo/build/bin directory. You should change
# to the alternative if you have lld defined already in your path somewhere.
export PATH=$PATH:$PWD/third_party/llvm-build/Release+Asserts/bin/

# build chromium
autoninja -C out/ufo chrome
