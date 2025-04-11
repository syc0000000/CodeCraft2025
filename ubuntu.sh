#!/bin/bash

uv venv
pyenv
chmod +777 ./test/interactor
nohup python ./run_all_periods_v2.py -workers 6
tail -f ./nohup.out
