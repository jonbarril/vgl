package com.vgl.cli.services;

import com.vgl.cli.services.StatusService.CommitInfo;
import com.vgl.cli.services.StatusService.StatusResult;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Date;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for StatusService - demonstrates fast unit testing without mocks first.
 * Can add Mockito later if needed.
 */
public class StatusServiceTest {
    
    @Test
    public void statusResultHasChangesWhenModifiedFiles() {
        Set<String> modified = Set.of("file1.txt");
        StatusResult result = new StatusResult(
            modified, 
            Collections.emptySet(), 
            Collections.emptySet(),
            Collections.emptySet(), 
            Collections.emptySet(),
            Collections.emptySet(),
            null
        );
        
        assertThat(result.hasChanges()).isTrue();
    }
    
    @Test
    public void statusResultNoChangesWhenAllEmpty() {
        StatusResult result = new StatusResult(
            Collections.emptySet(),
            Collections.emptySet(),
            Collections.emptySet(),
            Collections.emptySet(),
            Collections.emptySet(),
            Collections.emptySet(),
            null
        );
        
        assertThat(result.hasChanges()).isFalse();
    }
    
    @Test
    public void commitInfoStoresAllFields() {
        CommitInfo info = new CommitInfo("abc1234", "Test commit", new Date());
        
        assertThat(info.shortHash).isEqualTo("abc1234");
        assertThat(info.message).isEqualTo("Test commit");
        assertThat(info.date).isNotNull();
    }
}
