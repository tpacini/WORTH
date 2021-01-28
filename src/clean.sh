
if [ $(ls Client/ | wc -l) -gt 4 ]; then rm Client/*.class; fi
if [ $(ls Server/ | wc -l) -gt 5 ]; then rm Server/*.class; fi
if [ $(ls Commons/ | wc -l) -gt 4 ]; then rm Commons/*.class; fi 
