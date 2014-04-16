package org.motechproject.mds.service;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.motechproject.mds.service.impl.HistoryServiceImpl;
import org.motechproject.mds.testutil.records.Record;
import org.motechproject.mds.testutil.records.history.Record__History;
import org.motechproject.mds.testutil.records.history.Record__Trash;
import org.motechproject.mds.util.MDSClassLoader;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;
import javax.jdo.Query;
import java.util.ArrayList;
import java.util.List;

import static ch.lambdaj.Lambda.having;
import static ch.lambdaj.Lambda.on;
import static ch.lambdaj.Lambda.selectFirst;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

@RunWith(PowerMockRunner.class)
@PrepareForTest(MDSClassLoader.class)
public class HistoryServiceTest {

    @Mock
    private BundleContext bundleContext;

    @Mock
    private MDSClassLoader classLoader;

    @Mock
    private ServiceReference reference;

    @Mock
    private PersistenceManagerFactory factory;

    @Mock
    private PersistenceManager manager;

    @Mock
    private Query query;

    @Captor
    private ArgumentCaptor<Record__History> recordHistoryCaptor;

    @Captor
    private ArgumentCaptor<String> stringCaptor;

    private HistoryService historyService;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        historyService = new HistoryServiceImpl();
        ((HistoryServiceImpl) historyService).setPersistenceManagerFactory(factory);

        PowerMockito.mockStatic(MDSClassLoader.class);
        PowerMockito.when(MDSClassLoader.getInstance()).thenReturn(classLoader);

        doReturn(Record__History.class).when(classLoader).loadClass(Record__History.class.getName());
        doReturn(reference).when(bundleContext).getServiceReference(anyString());

        doReturn(manager).when(factory).getPersistenceManager();
        doReturn(query).when(manager).newQuery(Record__History.class);
    }

    @Test
    public void shouldNotRecordIfClassNotFound() throws Exception {
        doReturn(null).when(classLoader).loadClass(anyString());

        historyService.record(null);
        verifyZeroInteractions(manager);

        historyService.record(new Object());
        verifyZeroInteractions(manager);
    }

    @Test
    public void shouldCreateNewRecord() throws Exception {
        doReturn(null).when(query).execute(anyLong());

        Record instance = new Record();
        historyService.record(instance);

        verify(manager).makePersistent(recordHistoryCaptor.capture());

        Record__History history = recordHistoryCaptor.getValue();

        assertNull(history.getRecord__HistoryNext());
        assertNull(history.getRecord__HistoryPrevious());
        assertEquals(instance.getId(), history.getRecord__HistoryCurrentVersion());
        assertEquals(instance.getValue(), history.getValue());
    }

    @Test
    public void shouldCreateNewRecordAndConnectItWithPrevious() throws Exception {
        Record__History previous = new Record__History();
        previous.setRecord__HistoryCurrentVersion(1L);
        previous.setValue("value");

        doReturn(previous).when(query).execute(anyLong());

        Record instance = new Record();
        instance.setValue("other");

        historyService.record(instance);

        verify(manager, times(2)).makePersistent(recordHistoryCaptor.capture());

        List<Record__History> records = recordHistoryCaptor.getAllValues();

        Record__History previousDB = selectFirst(records, having(on(Record__History.class).getRecord__HistoryPrevious(), equalTo(null)));
        Record__History nextDB = selectFirst(records, having(on(Record__History.class).getRecord__HistoryNext(), equalTo(null)));

        assertEquals(instance.getId(), previousDB.getRecord__HistoryCurrentVersion());
        assertEquals(instance.getId(), nextDB.getRecord__HistoryCurrentVersion());

        assertEquals(previous.getValue(), previousDB.getValue());
        assertEquals(instance.getValue(), nextDB.getValue());

        assertEquals(previous, nextDB.getRecord__HistoryPrevious());
        assertEquals(nextDB, previousDB.getRecord__HistoryNext());
    }

    @Test
    public void shouldNotRemoveIfClassNotFound() throws Exception {
        doReturn(null).when(classLoader).loadClass(anyString());

        historyService.remove(null);
        verifyZeroInteractions(manager);

        historyService.remove(new Object());
        verifyZeroInteractions(manager);
    }

    @Test
    public void shouldRemoveAllHistoryRecordsRelatedWithGivenInstance() throws Exception {
        historyService.remove(new Record());

        verify(factory).getPersistenceManager();
        verify(manager).newQuery(Record__History.class);
        verify(query).setFilter(stringCaptor.capture());
        verify(query).declareParameters(stringCaptor.capture());
        verify(query).deletePersistentAll(1L);

        List<String> values = stringCaptor.getAllValues();

        assertThat(values, hasItem("record__HistoryCurrentVersion==param0"));
        assertThat(values, hasItem("java.lang.Long param0"));
    }

    @Test
    public void shouldNotSetTrashFlagIfClassNotFound() throws Exception {
        doReturn(null).when(classLoader).loadClass(anyString());

        historyService.setTrashFlag(null, null, true);
        verifyZeroInteractions(manager);

        historyService.setTrashFlag(new Object(), null, true);
        verifyZeroInteractions(manager);
    }

    @Test
    public void shouldSetTrashFlag() throws Exception {
        Record__History value = new Record__History(1L, "value");
        assertFalse(value.getRecord__HistoryFromTrash());

        List<Record__History> collection = new ArrayList<>();
        collection.add(value);

        doReturn(collection).when(query).execute(1L);

        Record__Trash trash = new Record__Trash();
        historyService.setTrashFlag(new Record(), trash, true);

        assertTrue(value.getRecord__HistoryFromTrash());
        assertEquals(trash.getId(), value.getRecord__HistoryCurrentVersion());
        verify(manager).makePersistent(value);
    }

}