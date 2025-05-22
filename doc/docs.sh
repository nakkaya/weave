#!/bin/bash

if [ ! -d "venv" ]; then
    python3 -m venv venv
    ./venv/bin/pip install -r requirements.txt
fi

./venv/bin/mkdocs serve -a 0.0.0.0:8000
