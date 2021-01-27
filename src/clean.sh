
if [ $(ls Client/ | wc -l) -gt 4 ]; then rm Client/*.class; fi
if [ $(ls Server/ | wc -l) -gt 6 ]; then rm Server/*.class; fi

