/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.contrib.guidedtour.internal;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Named;

import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.xwiki.contrib.guidedtour.api.dtos.TaskDTO;
import org.xwiki.contrib.guidedtour.api.enums.TourProperty;
import org.xwiki.contrib.guidedtour.api.exceptions.DuplicatedIdException;
import org.xwiki.contrib.guidedtour.api.exceptions.InvalidIdException;
import org.xwiki.contrib.guidedtour.internal.util.SolrQueryUtil;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.SpaceReference;
import org.xwiki.model.validation.EntityNameValidation;
import org.xwiki.query.QueryException;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.xwiki.contrib.guidedtour.internal.util.GuidedTourConstants.TASK_CLASS;

@ComponentTest
class TasksManagerTest
{
    private static final String CLASS_PREFIX = "property.XWiki.GuidedTour.TaskClass.%s";

    private static final String TOUR_ID = "tourId";

    private static final String TASK_ID1 = "taskId1";

    private static final String TASK_ID2 = "taskId2";

    private static final String VALIDATED_TASK_ID1 = "validatedTaskId1";

    private static final String VALIDATED_TASK_ID2 = "validatedTaskId2";

    private static final List<String> FL =
        List.of(TourProperty.DEPENDS_ON.formKey(CLASS_PREFIX), TourProperty.TITLE.formKey(CLASS_PREFIX),
            TourProperty.ORDER.formKey(CLASS_PREFIX), TourProperty.IS_ACTIVE.formKey(CLASS_PREFIX));

    private final SolrDocumentList solrDocumentList = new SolrDocumentList();

    @Mock
    private SolrDocument solrDocument1;

    @Mock
    private SolrDocument solrDocument2;

    @InjectMockComponents
    private TasksManager tasksManager;

    @MockComponent
    private XWikiContext wikiContext;

    @MockComponent
    private SolrQueryUtil queryUtil;

    @MockComponent
    @Named("current")
    private DocumentReferenceResolver<String> documentReferenceResolver;

    @MockComponent
    @Named("ReplaceCharacterEntityNameValidation")
    private EntityNameValidation nameValidator;

    @MockComponent
    @Named("local")
    private EntityReferenceSerializer<String> localSerializer;

    @MockComponent
    private DocumentReferenceResolver<SolrDocument> solrDocumentReferenceResolver;

    @Mock
    private SpaceReference spaceReference;

    @Mock
    private XWiki xwiki;

    @Mock
    private XWikiDocument tourDocument;

    @Mock
    private XWikiDocument taskDocument1;

    @Mock
    private XWikiDocument taskDocument2;

    @Mock
    private DocumentReference tourReference;

    @Mock
    private DocumentReference taskReference1;

    @Mock
    private DocumentReference taskReference2;

    @Mock
    private BaseObject taskObject1;

    @Mock
    private BaseObject taskObject2;

    private TaskDTO taskDTO1 = new TaskDTO(TASK_ID1, "task title1", 1, true, new ArrayList<>());

    private TaskDTO taskDTO2 = new TaskDTO(TASK_ID2, "task title2", -1, false, List.of(TASK_ID1));

    @BeforeEach
    void setup() throws XWikiException, QueryException
    {
        when(wikiContext.getWiki()).thenReturn(xwiki);
        when(xwiki.getDocument(tourReference, wikiContext)).thenReturn(tourDocument);
        when(xwiki.getDocument(taskReference1, wikiContext)).thenReturn(taskDocument1);
        when(xwiki.getDocument(taskReference2, wikiContext)).thenReturn(taskDocument2);
        when(xwiki.exists(tourReference, wikiContext)).thenReturn(true);
        when(xwiki.exists(taskReference1, wikiContext)).thenReturn(true);
        when(xwiki.exists(taskReference2, wikiContext)).thenReturn(true);

        when(nameValidator.transform(TASK_ID1)).thenReturn(VALIDATED_TASK_ID1);
        when(nameValidator.transform(TASK_ID2)).thenReturn(VALIDATED_TASK_ID2);

        when(documentReferenceResolver.resolve(TOUR_ID)).thenReturn(tourReference);
        when(documentReferenceResolver.resolve(VALIDATED_TASK_ID1, tourReference)).thenReturn(taskReference1);
        when(documentReferenceResolver.resolve(VALIDATED_TASK_ID2, tourReference)).thenReturn(taskReference2);
        when(solrDocumentReferenceResolver.resolve(solrDocument1, EntityType.DOCUMENT)).thenReturn(taskReference1);
        when(solrDocumentReferenceResolver.resolve(solrDocument2, EntityType.DOCUMENT)).thenReturn(taskReference2);

        when(tourReference.getLastSpaceReference()).thenReturn(spaceReference);
        when(localSerializer.serialize(spaceReference)).thenReturn("tourSpace");
        solrDocumentList.add(solrDocument1);
        solrDocumentList.add(solrDocument2);

        when(taskReference1.getName()).thenReturn(VALIDATED_TASK_ID1);
        when(taskReference2.getName()).thenReturn(VALIDATED_TASK_ID2);
        when(queryUtil.executeQuery("class:XWiki.GuidedTour.TaskClass AND ",
            "{!q.op=AND} type:DOCUMENT AND space:tourSpace", FL)).thenReturn(solrDocumentList);

        when(solrDocument1.getFirstValue(TourProperty.DEPENDS_ON.formKey(CLASS_PREFIX))).thenReturn("");
        when(solrDocument1.getFirstValue(TourProperty.TITLE.formKey(CLASS_PREFIX))).thenReturn(taskDTO1.getTitle());
        when(solrDocument1.getFirstValue(TourProperty.ORDER.formKey(CLASS_PREFIX))).thenReturn(1L);
        when(solrDocument1.getFirstValue(TourProperty.IS_ACTIVE.formKey(CLASS_PREFIX))).thenReturn(1);

        when(solrDocument2.getFirstValue(TourProperty.DEPENDS_ON.formKey(CLASS_PREFIX))).thenReturn(VALIDATED_TASK_ID1);
        when(solrDocument2.getFirstValue(TourProperty.TITLE.formKey(CLASS_PREFIX))).thenReturn(taskDTO2.getTitle());
        when(solrDocument2.getFirstValue(TourProperty.ORDER.formKey(CLASS_PREFIX))).thenReturn(2L);
        when(solrDocument2.getFirstValue(TourProperty.IS_ACTIVE.formKey(CLASS_PREFIX))).thenReturn(0);
    }

    @Test
    void createTask() throws XWikiException, DuplicatedIdException, InvalidIdException, QueryException
    {
        when(xwiki.exists(taskReference1, wikiContext)).thenReturn(false);
        when(taskDocument1.newXObject(TASK_CLASS, wikiContext)).thenReturn(taskObject1);
        taskDTO1.setOrder(0);
        tasksManager.createTask(TOUR_ID, taskDTO1);

        verify(taskDocument1, times(1)).setTitle(taskDTO1.getTitle());
        verify(taskObject1, times(1)).set("order", 3, wikiContext);
        verify(taskObject1, times(1)).set("dependsOn", taskDTO1.getDependsOn(), wikiContext);
        verify(xwiki, times(1)).saveDocument(taskDocument1, "Task created.", wikiContext);
    }

    @Test
    void createTaskSameOrder() throws XWikiException
    {
        when(xwiki.exists(taskReference1, wikiContext)).thenReturn(false);
        taskDTO1.setOrder(2);
        DuplicatedIdException exception = assertThrows(DuplicatedIdException.class, () -> {
            tasksManager.createTask(TOUR_ID, taskDTO1);
        });

        assertEquals("A task with the given order already exists.", exception.getMessage());
    }

    @Test
    void createTaskDuplicate()
    {

        DuplicatedIdException exception = assertThrows(DuplicatedIdException.class, () -> {
            tasksManager.createTask(TOUR_ID, taskDTO1);
        });

        assertEquals(String.format("Task page [%s] already exists.", taskReference1), exception.getMessage());
    }

    @Test
    void getTask() throws Exception
    {
        String fq = String.format("{!q.op=AND} type:DOCUMENT AND space:tourSpace AND name:%s", TASK_ID2);
        solrDocumentList.clear();
        solrDocumentList.add(solrDocument2);
        when(queryUtil.executeQuery("class:XWiki.GuidedTour.TaskClass AND ", fq, FL)).thenReturn(solrDocumentList);

        TaskDTO result = tasksManager.getTask(TOUR_ID, TASK_ID2);

        assertEquals(VALIDATED_TASK_ID2, result.getId());
        assertEquals(taskDTO2.getTitle(), result.getTitle());
        assertFalse(taskDTO2.isActive());
    }

    @Test
    void getTaskInvalidId() throws Exception
    {
        String fq = String.format("{!q.op=AND} type:DOCUMENT AND space:tourSpace AND name:%s", TASK_ID2);
        solrDocumentList.clear();
        when(queryUtil.executeQuery("class:XWiki.GuidedTour.TaskClass AND ", fq, FL)).thenReturn(solrDocumentList);

        InvalidIdException exception = assertThrows(InvalidIdException.class, () -> {
            tasksManager.getTask(TOUR_ID, TASK_ID2);
        });

        assertEquals(String.format("Task with the given id [%s] does not exists.", TASK_ID2), exception.getMessage());
    }

    @Test
    void getAllTasks() throws Exception
    {
        List<TaskDTO> tasks = tasksManager.getAllTasks(TOUR_ID);

        assertEquals(2, tasks.size());
        assertEquals(VALIDATED_TASK_ID1, tasks.get(0).getId());
        assertEquals(taskDTO1.getTitle(), tasks.get(0).getTitle());
        assertEquals(1, tasks.get(0).getOrder());
        assertTrue(tasks.get(0).isActive());
        assertTrue(tasks.get(0).getDependsOn().isEmpty());

        assertEquals(VALIDATED_TASK_ID2, tasks.get(1).getId());
        assertEquals(taskDTO2.getTitle(), tasks.get(1).getTitle());
        assertEquals(2, tasks.get(1).getOrder());
        assertFalse(tasks.get(1).isActive());
        assertEquals(VALIDATED_TASK_ID1, tasks.get(1).getDependsOn().get(0));
    }

    @Test
    void updateTaskSameOrder() throws Exception
    {
        TaskDTO taskDtoUpdate = new TaskDTO(VALIDATED_TASK_ID2, "updated title", 2, true, new ArrayList<>());
        when(taskDocument2.getXObject(TASK_CLASS)).thenReturn(taskObject2);

        tasksManager.updateTask(TOUR_ID, taskDtoUpdate);

        verify(taskDocument2, times(1)).setTitle(taskDtoUpdate.getTitle());
        verify(taskObject2, times(1)).set("order", 2, wikiContext);
        verify(taskObject2, times(1)).set("dependsOn", taskDtoUpdate.getDependsOn(), wikiContext);
        verify(xwiki, times(1)).saveDocument(taskDocument2, "Updated task.", wikiContext);
    }

    @Test
    void updateTaskDifferentOrder() throws Exception
    {
        when(taskDocument1.getXObject(TASK_CLASS)).thenReturn(taskObject1);
        when(taskDocument2.getXObject(TASK_CLASS)).thenReturn(taskObject2);
        TaskDTO taskDtoUpdate = new TaskDTO(VALIDATED_TASK_ID2, "updated title", 1, true, new ArrayList<>());

        tasksManager.updateTask(TOUR_ID, taskDtoUpdate);

        verify(taskDocument2, times(1)).setTitle(taskDtoUpdate.getTitle());
        verify(taskObject2, times(1)).set("order", 1, wikiContext);
        verify(taskObject2, times(1)).set("dependsOn", taskDtoUpdate.getDependsOn(), wikiContext);
        verify(xwiki, times(1)).saveDocument(taskDocument2, "Updated task.", wikiContext);
    }

    @Test
    void updateTaskInvalidTourId()
    {
        String invalidStringId = "TOUR_ID";
        InvalidIdException exception = assertThrows(InvalidIdException.class, () -> {
            tasksManager.updateTask(invalidStringId, new TaskDTO());
        });

        assertEquals(String.format("Tour with the given id [%s] does not exists.", invalidStringId),
            exception.getMessage());
    }

    @Test
    void deleteTask() throws Exception
    {
        when(taskDocument2.getXObject(TASK_CLASS)).thenReturn(taskObject2);
        tasksManager.deleteTask(TOUR_ID, VALIDATED_TASK_ID1);

        verify(xwiki, times(1)).deleteAllDocuments(taskDocument1, wikiContext);
    }
}
