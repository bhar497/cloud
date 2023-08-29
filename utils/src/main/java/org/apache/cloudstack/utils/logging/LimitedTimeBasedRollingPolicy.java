package org.apache.cloudstack.utils.logging;

import org.apache.log4j.Appender;
import org.apache.log4j.pattern.PatternConverter;
import org.apache.log4j.rolling.RollingPolicyBase;
import org.apache.log4j.rolling.RolloverDescription;
import org.apache.log4j.rolling.RolloverDescriptionImpl;
import org.apache.log4j.rolling.TriggeringPolicy;
import org.apache.log4j.rolling.helper.Action;
import org.apache.log4j.rolling.helper.FileRenameAction;
import org.apache.log4j.rolling.helper.GZCompressAction;
import org.apache.log4j.rolling.helper.ZipCompressAction;
import org.apache.log4j.spi.LoggingEvent;

import java.io.File;
import java.io.FilenameFilter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/*
This file is mostly a copy of the TimeBasedRollingPolicy class. I would have rather extended that class, but it is marked final.
 */
public final class LimitedTimeBasedRollingPolicy extends RollingPolicyBase implements TriggeringPolicy {
    private long nextCheck = 0L;
    private String lastFileName = null;
    private int suffixLength = 0;

    private int maxBackups = 30;

    public LimitedTimeBasedRollingPolicy() {
    }

    public void activateOptions() {
        super.activateOptions();
        PatternConverter dtc = this.getDatePatternConverter();
        if (dtc == null) {
            throw new IllegalStateException("FileNamePattern [" + this.getFileNamePattern() + "] does not contain a valid date format specifier");
        } else {
            long n = System.currentTimeMillis();
            StringBuffer buf = new StringBuffer();
            this.formatFileName(new Date(n), buf);
            this.lastFileName = buf.toString();
            this.suffixLength = 0;
            if (this.lastFileName.endsWith(".gz")) {
                this.suffixLength = 3;
            } else if (this.lastFileName.endsWith(".zip")) {
                this.suffixLength = 4;
            }

        }
    }

    public RolloverDescription initialize(String currentActiveFile, boolean append) {
        long n = System.currentTimeMillis();
        this.nextCheck = (n / 1000L + 1L) * 1000L;
        StringBuffer buf = new StringBuffer();
        this.formatFileName(new Date(n), buf);
        this.lastFileName = buf.toString();
        if (this.activeFileName != null) {
            return new RolloverDescriptionImpl(this.activeFileName, append, (Action) null, (Action) null);
        } else {
            return currentActiveFile != null ? new RolloverDescriptionImpl(currentActiveFile, append, (Action) null, (Action) null) : new RolloverDescriptionImpl(this.lastFileName.substring(0, this.lastFileName.length() - this.suffixLength), append, (Action) null, (Action) null);
        }
    }

    public RolloverDescription rollover(String currentActiveFile) {
        long n = System.currentTimeMillis();
        this.nextCheck = (n / 1000L + 1L) * 1000L;
        StringBuffer buf = new StringBuffer();
        this.formatFileName(new Date(n), buf);
        String newFileName = buf.toString();
        if (newFileName.equals(this.lastFileName)) {
            return null;
        } else {
            cleanupOldFiles();

            Action renameAction = null;
            Action compressAction = null;
            String lastBaseName = this.lastFileName.substring(0, this.lastFileName.length() - this.suffixLength);
            String nextActiveFile = newFileName.substring(0, newFileName.length() - this.suffixLength);
            if (!currentActiveFile.equals(lastBaseName)) {
                renameAction = new FileRenameAction(new File(currentActiveFile), new File(lastBaseName), true);
                nextActiveFile = currentActiveFile;
            }

            if (this.suffixLength == 3) {
                compressAction = new GZCompressAction(new File(lastBaseName), new File(this.lastFileName), true);
            }

            if (this.suffixLength == 4) {
                compressAction = new ZipCompressAction(new File(lastBaseName), new File(this.lastFileName), true);
            }

            this.lastFileName = newFileName;
            return new RolloverDescriptionImpl(nextActiveFile, false, renameAction, (Action) compressAction);
        }
    }

    private void cleanupOldFiles() {
        List<ModifiedTimeSortableFile> files = getAllFiles();
        Collections.sort(files);
        if(files.size() > maxBackups)
        {
            int index = 0;
            int diff = files.size() - (maxBackups);
            for(ModifiedTimeSortableFile file : files)
            {
                if(index >= diff)
                    break;

                file.delete();
                index++;
            }
        }
    }

    public boolean isTriggeringEvent(Appender appender, LoggingEvent event, String filename, long fileLength) {
        return System.currentTimeMillis() >= this.nextCheck;
    }

    List<ModifiedTimeSortableFile> getAllFiles()
    {
        String fileName = activeFileName;
        List<ModifiedTimeSortableFile> files = new ArrayList<>();
        String fileNamePattern = this.getFileNamePattern();
        File patternFile = new File(fileNamePattern);
        String parentDir = patternFile.getParent();
        if (parentDir != null) {
            fileNamePattern = fileNamePattern.substring(parentDir.length() + 1);
        }
        String finalSearchPattern = fileNamePattern
                .replaceFirst("%(d|date|i|index)\\{[^}]*\\}", "*");
        FilenameFilter newFilter = (d, s) -> s.matches(finalSearchPattern);
        File file = new File(fileName);
        String parentDirectory = file.getParent();
        if(file.exists())
        {
            if(file.getParent() == null){
                String absolutePath = file.getAbsolutePath();
                parentDirectory = absolutePath.substring(0,
                        absolutePath.lastIndexOf(fileName));
            }
        }
        File dir = new File(parentDirectory);
        String[] names = dir.list(newFilter);

        for (int i = 0 ; i < names.length ; i++) {
            files.add(new ModifiedTimeSortableFile(dir + System.getProperty("file.separator") + names[i]));
        }
        return files;
    }

    public int getMaxBackups() {
        return maxBackups;
    }

    public void setMaxBackups(int maxBackups) {
        this.maxBackups = maxBackups;
    }
}

class ModifiedTimeSortableFile extends File implements Serializable, Comparable<File>
{
    private static final long serialVersionUID = 1373373728209668895L;

    public ModifiedTimeSortableFile(String string) {
        super(string);
    }

    public int compareTo(File anotherPathName) {
        long thisVal = this.lastModified();
        long anotherVal = anotherPathName.lastModified();
        return (thisVal<anotherVal ? -1 : (thisVal==anotherVal ? 0 : 1));
    }
}
