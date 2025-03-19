javac -d build ./Main.java
rm -rf logs/app.log
# 检查是否有-release参数
DEBUG_OPTION="-d"
for arg in "$@"; do
    if [ "$arg" = "-release" ]; then
        DEBUG_OPTION=""
        break
    fi
done
# 检查是否有-big参数
INPUT_FILE="./test/sample.in"
for arg in "$@"; do
    if [ "$arg" = "-big" ]; then
        INPUT_FILE="./test/sample_practice.in"
        break
    fi
done


# 检测操作系统类型
if [ "$(uname -s)" = "Darwin" ]; then
    # Mac系统
    python3 ./test/run.py ./test/interactor_mac $INPUT_FILE "java -cp ./build Main" $DEBUG_OPTION -r 39260 39261
else
    # 其他系统
    python3 ./test/run.py ./test/interactor $INPUT_FILE "java -cp ./build Main" $DEBUG_OPTION -r 39260 39261
fi
