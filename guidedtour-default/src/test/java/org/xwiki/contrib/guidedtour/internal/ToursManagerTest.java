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
import javax.inject.Provider;

import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.xwiki.contrib.guidedtour.api.dtos.TourDTO;
import org.xwiki.contrib.guidedtour.api.enums.TourProperty;
import org.xwiki.contrib.guidedtour.api.exceptions.DuplicatedIdException;
import org.xwiki.contrib.guidedtour.api.exceptions.InvalidIdException;
import org.xwiki.contrib.guidedtour.internal.util.SolrQueryUtil;
import org.xwiki.job.JobExecutor;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.SpaceReference;
import org.xwiki.refactoring.job.EntityRequest;
import org.xwiki.refactoring.job.RefactoringJobs;
import org.xwiki.refactoring.script.RequestFactory;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.xwiki.contrib.guidedtour.internal.util.GuidedTourConstants.TOUR_CLASS;

@ComponentTest
class ToursManagerTest
{
    private static final String CLASS_PREFIX = "property.XWiki.GuidedTour.TourClass.%s";

    private static final String TOUR_ID = "tourId";

    private final SolrDocumentList solrDocumentList = new SolrDocumentList();

    private final TourDTO tourDTO = new TourDTO(TOUR_ID, "dto Title", true);

    private final TourDTO tourDTOUpdated = new TourDTO(TOUR_ID, "updated title", false);

    @InjectMockComponents
    private ToursManager toursManager;

    @MockComponent
    private Provider<XWikiContext> wikiContextProvider;

    @MockComponent
    @Named("current")
    private DocumentReferenceResolver<String> documentReferenceResolver;

    @MockComponent
    private DocumentReferenceResolver<SolrDocument> solrDocumentReferenceResolver;

    @MockComponent
    private TasksManager tasksManager;

    @MockComponent
    private SolrQueryUtil queryUtil;

    @MockComponent
    private JobExecutor jobExecutor;

    @MockComponent
    private RequestFactory requestFactory;

    @Mock
    private XWikiContext wikiContext;

    @Mock
    private XWiki xwiki;

    @Mock
    private DocumentReference documentReference;

    @Mock
    private XWikiDocument xwikiDocument;

    @Mock
    private BaseObject baseObject;

    @Mock
    private SolrDocument solrDocument;

    @Mock
    private SpaceReference spaceReference;

    @Mock
    private EntityRequest deleteReq;

    @BeforeEach
    void setup() throws XWikiException
    {
        when(wikiContextProvider.get()).thenReturn(wikiContext);
        when(wikiContext.getWiki()).thenReturn(xwiki);
        when(documentReferenceResolver.resolve(TOUR_ID)).thenReturn(documentReference);
        when(xwiki.getDocument(documentReference, wikiContext)).thenReturn(xwikiDocument);
        when(xwikiDocument.newXObject(TOUR_CLASS, wikiContext)).thenReturn(baseObject);
        when(xwikiDocument.getXObject(TOUR_CLASS)).thenReturn(baseObject);
        solrDocumentList.add(solrDocument);
    }

    @Test
    void createTour() throws XWikiException, DuplicatedIdException
    {
        when(xwikiDocument.getXObject(TOUR_CLASS)).thenReturn(null);

        toursManager.createTour(tourDTO);
        verify(baseObject, times(1)).set("title", tourDTO.getTitle(), wikiContext);
        verify(xwiki, times(1)).saveDocument(xwikiDocument, "Tour created.", wikiContext);
    }

    @Test
    void createTourDuplicate()
    {
        DuplicatedIdException exception = assertThrows(DuplicatedIdException.class, () -> {
            this.toursManager.createTour(tourDTO);
        });
        assertEquals(exception.getMessage(),
            String.format("A tour with the same ID [%s] already exists.", tourDTO.getId()));
    }

    @Test
    void getAllTours() throws Exception
    {
        when(queryUtil.executeQuery("class:XWiki.GuidedTour.TourClass", "type:DOCUMENT",
            List.of(TourProperty.TITLE.formKey(CLASS_PREFIX),
                TourProperty.IS_ACTIVE.formKey(CLASS_PREFIX)))).thenReturn(solrDocumentList);
        when(solrDocument.getFirstValue("property.XWiki.GuidedTour.TourClass.title_string")).thenReturn("tour title");
        when(solrDocument.getFirstValue("property.XWiki.GuidedTour.TourClass.isActive_int")).thenReturn(1);
        when(solrDocumentReferenceResolver.resolve(solrDocument, EntityType.DOCUMENT)).thenReturn(documentReference);
        when(tasksManager.getAllTasks(documentReference.toString())).thenReturn(new ArrayList<>());

        List<TourDTO> tours = toursManager.getAllTours();
        assertEquals(1, tours.size());
        assertEquals("tour title", tours.get(0).getTitle());
        assertTrue(tours.get(0).isActive());
        assertTrue(tours.get(0).getTasksList().isEmpty());
    }

    @Test
    void updateTour() throws Exception
    {
        when(baseObject.getOwnerDocument()).thenReturn(xwikiDocument);
        toursManager.updateTour(tourDTOUpdated);
        verify(baseObject, times(1)).set("title", tourDTOUpdated.getTitle(), wikiContext);
        verify(baseObject, times(1)).set("isActive", 0, wikiContext);
        verify(xwiki, times(1)).saveDocument(xwikiDocument, "Updated tour object.", wikiContext);
    }

    @Test
    void updateTourInvalidId()
    {
        when(xwikiDocument.getXObject(TOUR_CLASS)).thenReturn(null);
        InvalidIdException exception = assertThrows(InvalidIdException.class, () -> {
            toursManager.updateTour(tourDTOUpdated);
        });
        assertEquals(exception.getMessage(), String.format("Tour with the given id [%s] does not exist.", TOUR_ID));
    }

    @Test
    void deleteTour() throws Exception
    {
        when(documentReference.getLastSpaceReference()).thenReturn(spaceReference);
        when(requestFactory.createDeleteRequest(List.of(documentReference.getLastSpaceReference()))).thenReturn(
            deleteReq);
        toursManager.deleteTour(TOUR_ID);
        verify(jobExecutor, times(1)).execute(RefactoringJobs.DELETE, deleteReq);
    }

    @Test
    void deleteTourInvalidId()
    {
        when(xwikiDocument.getXObject(TOUR_CLASS)).thenReturn(null);
        InvalidIdException exception = assertThrows(InvalidIdException.class, () -> {
            toursManager.deleteTour(TOUR_ID);
        });
        assertEquals(exception.getMessage(), String.format("Tour with the given id [%s] does not exist.", TOUR_ID));
    }
}
