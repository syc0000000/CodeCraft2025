javac -d build ./Main.java

# 检查是否有-release参数
DEBUG_OPTION="-d"
for arg in "$@"; do
    if [ "$arg" = "-release" ]; then
        DEBUG_OPTION=""
        break
    fi
done

# 检测操作系统类型
if [ "$(uname -s)" = "Darwin" ]; then
    # Mac系统
    python3 ./test/run.py ./test/interactor_mac ./test/sample.in "java -cp ./build Main" $DEBUG_OPTION
else
    # 其他系统
    python3 ./test/run.py ./test/interactor ./test/sample.in "java -cp ./build Main" $DEBUG_OPTION
fi
