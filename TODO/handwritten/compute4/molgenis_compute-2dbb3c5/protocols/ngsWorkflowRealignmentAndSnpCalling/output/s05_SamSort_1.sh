



##### BEFORE #####
touch $PBS_O_WORKDIR/s05_SamSort_1.out
source $WORKDIR/tools/scripts/import.sh
before="$(date +%s)"
echo "Begin job s05_SamSort_1 at $(date)" >> $PBS_O_WORKDIR/RUNTIME.log

echo Running on node: `hostname`

sleep 60
###### MAIN ######

#
# =====================================================
# $Id$
# $URL$
# $LastChangedDate$
# $LastChangedRevision$
# $LastChangedBy$
# =====================================================
#

#MOLGENIS walltime=35:59:00 mem=4
#FOREACH

module load picard-tools/1.61

getFile $WORKDIR/tmp//demo/output//120308_SN163_0457_BD0E5CACXX_L4_CAACCT.human_g1k_v37.bam

java -jar -Xmx3g $PICARD_HOME//SortSam.jar \
INPUT=$WORKDIR/tmp//demo/output//120308_SN163_0457_BD0E5CACXX_L4_CAACCT.human_g1k_v37.bam \
OUTPUT=$WORKDIR/tmp//demo/output//120308_SN163_0457_BD0E5CACXX_L4_CAACCT.human_g1k_v37.sorted.bam \
SORT_ORDER=coordinate \
VALIDATION_STRINGENCY=LENIENT \
MAX_RECORDS_IN_RAM=1000000 \
TMP_DIR=$WORKDIR/tmp/processing/

java -jar -Xmx3g $PICARD_HOME//BuildBamIndex.jar \
INPUT=$WORKDIR/tmp//demo/output//120308_SN163_0457_BD0E5CACXX_L4_CAACCT.human_g1k_v37.sorted.bam \
OUTPUT=$WORKDIR/tmp//demo/output//120308_SN163_0457_BD0E5CACXX_L4_CAACCT.human_g1k_v37.sorted.bam.bai \
VALIDATION_STRINGENCY=LENIENT \
MAX_RECORDS_IN_RAM=1000000 \
TMP_DIR=$WORKDIR/tmp/processing/

putFile $WORKDIR/tmp//demo/output//120308_SN163_0457_BD0E5CACXX_L4_CAACCT.human_g1k_v37.sorted.bam
putFile $WORKDIR/tmp//demo/output//120308_SN163_0457_BD0E5CACXX_L4_CAACCT.human_g1k_v37.sorted.bam.bai

###### AFTER ######
after="$(date +%s)"
elapsed_seconds="$(expr $after - $before)"
echo Completed s05_SamSort_1 at $(date) in $elapsed_seconds seconds >> $PBS_O_WORKDIR/RUNTIME.log
touch $PBS_O_WORKDIR/s05_SamSort_1.finished
######## END ########

