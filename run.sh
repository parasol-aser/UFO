#!/bin/sh

# Install UFO (make take a couple of hours)
export BRANCH=release_70
git clone http://llvm.org/git/llvm.git -b $BRANCH
git clone http://llvm.org/git/clang.git llvm/tools/clang -b $BRANCH
git clone http://llvm.org/git/clang-tools-extra.git llvm/tools/clang/tools/extra -b $BRANCH
mv ufo-rt llvm/projects/
mkdir build
cd build
cmake -G "Unix Makefiles" ../llvm
make -j8
export PATH=./bin:$PATH

# Run on pbzip2 (pretty fast)
cd ../test/pbzip2-0.9.4
make
UFO_CALL=1 UFO_ON=1 UFO_TDIR=./ufo_test_trace ./pbzip2 -k -f -p3 ../test.tar
cd ../
java -jar runufo.jar --tdir pbzip2-0.9.4/ufo_test_trace

