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
INPUT_FILE="./test/sample_official.in"
for arg in "$@"; do
  if [ "$arg" = "-big" ]; then
    INPUT_FILE="./test/sample_practice.in"
    break
  fi
done

# 检测操作系统类型
if [ "$(uname -s)" = "Darwin" ]; then
  # Mac系统
  python3 ./test/run.py ./test/interactor_mac $INPUT_FILE "java -cp ./build Main" $DEBUG_OPTION
else
    # 其他系统
    python3 ./test/run.py ./test/interactor $INPUT_FILE "java -cp ./build Main -load distributions/distribution_20250330_135024.ser -loadTags tags/sortedTags_20250330_135111.ser" $DEBUG_OPTION
    # python3 ./test/run.py ./test/interactor $INPUT_FILE "java -cp ./build Main" $DEBUG_OPTION
fi
