#! /bin/ksh
###############################################################################
##
##                          Webonthgo Backend Process
##
##    -Overview
##    -This script is designed to run WebOnTheGo's backend processes
##    
##    -ChangeLog
##    -6/6/2011-dta- Added this script
##    -9/5/2011-Jalcanta- Implement anti-pileup routine
##
##
###############################################################################
#
. ${HOME}/.profile
# Error messaging function
#
error_message(){
if [ $# -lt 1 ] || [ $# -gt 2 ]; then
   echo "No argument sent.\n\nUsage:\n\terror_message \[message_text\|filename\] \{F\|W default Fatal\}">/tmp/error_message.$$
elif [ "x$1" = "x" ]; then
   echo "Null argument 1. \n\nUsage:\n\terror_message \[message_text\|filename\] \{F\|W default Fatal\}">/tmp/error_message.$$
else
   if [ ! -f $1 ]; then
      echo $1>/tmp/error_message.$$
   else
      if [ -s $1 ]; then
         mv $1 /tmp/error_message.$$
      else
         echo "File is empty.\n\nUsage:\n\terror_message \[message_text\|filename\]">/tmp/error_message.$$
      fi
   fi
fi
mailx -s "$0 Error at $(hostname)" -r tscdba@telscape.net -c omsreports2@telscape.net ossops@telscape.net < /tmp/error_message.$$

rm /tmp/error_message.$$
if [ "x$2" = "x" ] || [ "x$2" = "xF" ]; then
   exit 1
fi
}

# main
# 
# Juan A - Apr 30, 2012 - Set blackout dates to:
#
# Start at Last day of month at 11:03 pm
# End at   First day of month at 6:03 pm
#
curr_day=$(date '+%d')
curr_month=$(date '+%m')
curr_year=$(date '+%Y')
curr_hour=$(date '+%H')
curr_min=$(date '+%M')

if [ ${curr_month} == '12' ]; then
   next_month='01'
   next_year=$(expr ${curr_year} + 1)
   next_year=$(printf "%04d" ${next_year})
else
   next_month=$(expr ${curr_month} + 1)
   next_month=$(printf "%02d" ${next_month})
   next_year=${curr_year}
fi

first_day="01"
# get last day of the month
last_day=$(cal ${curr_month} ${curr_year} | grep -v '^$'|tail -1|awk '{n=split($0,a);print a[n]}')

if [ -f ./.start_blackout_dt.tmp ]; then
   start_blackout_dt=$(cat ./.start_blackout_dt.tmp)
else
   start_blackout_dt="${curr_year}${curr_month}${last_day}2303"
fi

if [ -f ./.end_blackout_dt.tmp ]; then
   end_blackout_dt=$(cat ./.end_blackout_dt.tmp)
else
   end_blackout_dt="${next_year}${next_month}${first_day}1803"
fi

curr_exec_dt=${curr_year}${curr_month}${curr_day}${curr_hour}${curr_min}

# All string dates should have a length of 12 YYYYMMDDHHMI
for i in "${curr_exec_dt}" "${start_blackout_dt}" "${end_blackout_dt}"
do
   if [ $(printf "%s" ${i}|wc -c) -ne 12 ]; then
      error_message "Malformed blackout date $i"
   fi
done

if [ "${start_blackout_dt}" = "${curr_exec_dt}" ]; then
   echo "${start_blackout_dt}" > ./.start_blackout_dt.tmp
   if [ $? -ne 0 ];then
      error_message "Could not create ./.start_blackout_dt.tmp"
   fi
   echo "${end_blackout_dt}"   > ./.end_blackout_dt.tmp
   if [ $? -ne 0 ];then
      error_message "Could not create ./.end_blackout_dt.tmp"
   fi
   error_message "Blackout starting effective $(date)" W
fi

if [ "${end_blackout_dt}" = "${curr_exec_dt}" ]; then
   rm ./.start_blackout_dt.tmp
   if [ $? -ne 0 ];then
      error_message "Could not remove ./.start_blackout_dt.tmp"
   fi
   rm ./.end_blackout_dt.tmp
   if [ $? -ne 0 ];then
      error_message "Could not remove ./.end_blackout_dt.tmp"
   fi
   error_message "Blackout ended as of $(date)" W
fi

if [ ${curr_exec_dt} -ge ${start_blackout_dt} ] && \
   [ ${curr_exec_dt} -le ${end_blackout_dt} ]; then
   #echo "Execution Date: ${curr_exec_dt}"
   #echo "Start Blackout Date: ${start_blackout_dt}"
   #echo "End   Blackout Date: ${end_blackout_dt}"
   echo "Blackout in progress. Exitting..." 
   exit 0 
fi
# Testing...
#echo "Execution Date: ${curr_exec_dt}"
#echo "Start Blackout Date: ${start_blackout_dt}"
#echo "End   Blackout Date: ${end_blackout_dt}"
#exit 0
# End of blackout  dates handling

# Do not let this job get stuck for more than 24 hrs
find /tmp -name WebonthegoBackEnd_Proc.flg -ctime +0 -print 1>$$.tmp 2>>/dev/null

if [ -s $$.tmp ]; then
   error_message "UNIX job $0 in $(whoami)@$(hostname) has been waiting for another processes for more than 24 hrs...\n" W
fi

rm $$.tmp

# Avoid pile ups
if [ -f /tmp/WebonthegoBackEnd_Proc.flg ]; then
   echo "Another instance of program $0 is still running! Aborting execution ..."
   exit 1
else
   touch /tmp/WebonthegoBackEnd_Proc.flg
   if [ $? -ne 0 ]; then
      error_message "Unable to create flag file /tmp/TrueConnectBackEnd_Proc.flg"
   fi
fi

###############################################################################
## JAVA CLASSPATH CONFIGURATION
## - hibernate and logging configurations are located in the bin dir
##   they are also repeated in the resources folder
## - configuration files in the resource folder are not necessary and
##   may be removed in a future release
###############################################################################
PROJECT=$HOME/tscp/mvne/webonthego
##########################
## SUPPORTING LIBRARIES ##
##########################
CLASSPATH=$CLASSPATH:$PROJECT/bin/
CLASSPATH=$CLASSPATH:$PROJECT/lib/ojdbc14.jar
CLASSPATH=$CLASSPATH:$PROJECT/lib/TSCPMVNA-API.jar
CLASSPATH=$CLASSPATH:$PROJECT/lib/mail.jar
CLASSPATH=$CLASSPATH:$PROJECT/lib/jta-1.1.jar
CLASSPATH=$CLASSPATH:$PROJECT/lib/javassist-3.12.0.GA.jar
CLASSPATH=$CLASSPATH:$PROJECT/lib/hibernate3.jar
CLASSPATH=$CLASSPATH:$PROJECT/lib/dom4j-1.6.1.jar
CLASSPATH=$CLASSPATH:$PROJECT/lib/commons-collections-3.1.jar
CLASSPATH=$CLASSPATH:$PROJECT/lib/antlr-2.7.6.jar
CLASSPATH=$CLASSPATH:$PROJECT/resources/
CLASSPATH=$CLASSPATH:$PROJECT/lib/webonthegobackend.jar
CLASSPATH=$CLASSPATH:$HOME/SUNWappserver/lib/activation.jar
#######################
## LOGGING LIBRARIES ##
#######################
CLASSPATH=$CLASSPATH:$PROJECT/lib/slf4j-api-1.6.1.jar
CLASSPATH=$CLASSPATH:$PROJECT/lib/logback-classic-0.9.30.jar
CLASSPATH=$CLASSPATH:$PROJECT/lib/logback-core-0.9.30.jar
#CLASSPATH=$CLASSPATH:$PROJECT/lib/log4j-1.2.16.jar
#CLASSPATH=$CLASSPATH:$PROJECT/lib/slf4j-log4j12-1.6.1.jar


#echo 'Classpath:'
#echo $CLASSPATH
logfile=/tmp/WebonthegoBackend_Proc.log
errfile=/tmp/WebonthegoBackend_Proc.err

echo "Starting @ $(date)" >> ${logfile}

my_pwd="${HOME}/tscp/mvne/webonthego"
if [ ! -d ${my_pwd} ]; then
   # Remove pile-up flag

   if [ -f /tmp/WebonthegoBackEnd_Proc.flg ]; then
      rm /tmp/WebonthegoBackEnd_Proc.flg
      if [ $? -ne 0 ]; then
         error_message "Unable to remove flag file /tmp/WebonthegoBackEnd_Proc.flg"
      fi
   fi
   error_message "Directory ${my_pwd} does not exist"
fi

cd ${my_pwd}
java -cp $CLASSPATH com.tc.bu.WebOnTheGoBackend 1>>${logfile} 2>>${errfile}
if [ $? -ne 0 ]; then
   error_message ${errfile} W
fi

echo "Ending @ $(date)" >> ${logfile}

sleep 5

# Java call is now complete

# Keep the last 800 lines of logging purposes
for f in ${logfile} ${errfile}
do
   if [ -s ${f} ]; then
      tail -800 ${f} > /tmp/$$.tmp && mv /tmp/$$.tmp ${f}
   fi
done


# Remove pile-up flag

if [ -f /tmp/WebonthegoBackEnd_Proc.flg ]; then
   rm   /tmp/WebonthegoBackEnd_Proc.flg
   if [ $? -ne 0 ]; then
      error_message "Unable to remove flag file /tmp/WebonthegoBackEnd_Proc.flg"
   fi
fi

# Ending

exit 0