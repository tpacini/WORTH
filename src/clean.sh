files1=(Client/)
files2=(Server/)

if [ ${#files1[@]} -gt 4 ]; then rm Client/*.class; fi
if [ ${#files2[@]} -gt 6 ]; then rm Server/*.class; fi

