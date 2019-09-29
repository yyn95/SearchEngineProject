#!/bin/sh
java -cp classes -Xmx1g ir.Engine -d ../dataset/davisWiki -l ir19.gif -p patterns.txt -idxType HashedIndex \
    |& tee davisWiki_out.log
