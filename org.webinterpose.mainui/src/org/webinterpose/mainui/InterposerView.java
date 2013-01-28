package org.webinterpose.mainui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Vector;

import org.eclipse.core.databinding.DataBindingContext;
import org.eclipse.core.databinding.beans.PojoObservables;
import org.eclipse.core.databinding.observable.list.WritableList;
import org.eclipse.core.databinding.observable.map.IObservableMap;
import org.eclipse.jface.databinding.viewers.ObservableListContentProvider;
import org.eclipse.jface.databinding.viewers.ObservableMapLabelProvider;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.EditingSupport;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.TextCellEditor;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.wb.swt.TableViewerColumnSorter;
import org.webinterpose.interposer.ServerMainSocket;
import org.webinterpose.interposer.ServerMainSocket.AddChildAdapter;

public class InterposerView extends ViewPart {
	public InterposerView() {
	}
	public static final String ID = "org.webinterpose.mainui.view";

	private TableViewer tableViewer;

	private WritableList mappings;

	private IMemento memento;
	private Text txtPort;
	private Text txtDomain;
	private Text txtBaseFolder;
	private String baseFolder;
	boolean running = false;

	final Vector<String> urlsToAdd = new Vector<>();
	final Map<String, Mapping> mapOfAllMapping = new TreeMap<>();
	public final Map<String, Mapping> mapOfMappedMapping = new TreeMap<>();
	
	private void addToMapping(Collection<Mapping> c) {
		Vector<Mapping> ms = new Vector<>();
		for (Mapping m : c) {
			if (!mapOfAllMapping.containsKey(m.distantFileUrl)) {
				mapOfAllMapping.put(m.distantFileUrl, m);
				ms.add(m);
				
				if (m.localFilePath != null && m.localFilePath != "") {
					mapOfMappedMapping.put(m.distantFileUrl, m);
				}
			}
		}
		mappings.addAll(ms);
	}

	private void addToMappingUrls(Collection<String> c) {
		Vector<Mapping> ms = new Vector<>();
		for (String m : c) {
			if (!mapOfAllMapping.containsKey(m)) {
				Mapping mapping = new Mapping(m);
				mapOfAllMapping.put(m, mapping);
				ms.add(mapping);
			}
		}
		mappings.addAll(ms);
	}
	
	final Runnable addMappingToAddRunnable = new Runnable() {

		@Override
		public void run() {
			synchronized (urlsToAdd) {
				if (urlsToAdd.size() > 0) {
					addToMappingUrls(urlsToAdd);
					urlsToAdd.clear();
				}
			}
		}
	};
	private TableColumn tblclmnDistantUrl;
	public class Mapping {
		String distantFileUrl;
		String localFilePath;
		public Mapping(String url) { distantFileUrl = url; }
		public Mapping(String url, String path) { distantFileUrl = url; localFilePath = path;}
		public String getLocalDirectory() {return baseFolder; }
		public String getLocalFilePath() { return localFilePath; }
		public void setLocalFilePath(String path) { this.localFilePath = path; }
		public String getDistantFileUrl() {	return distantFileUrl; }
	}

	public void createPartControl(Composite parent) {
		mappings = new WritableList();

		parent.setLayout(new FormLayout());

		final Button btnStart = new Button(parent, SWT.NONE);
		FormData fd_btnStart = new FormData();
		fd_btnStart.top = new FormAttachment(0, 10);
		fd_btnStart.left = new FormAttachment(0, 10);
		btnStart.setLayoutData(fd_btnStart);
		btnStart.setText("Start");

		tableViewer = new TableViewer(parent, SWT.BORDER | SWT.FULL_SELECTION);
		Table table = tableViewer.getTable();
		table.setLinesVisible(true);
		table.setHeaderVisible(true);
		FormData fd_table = new FormData();
		fd_table.bottom = new FormAttachment(100, -10);
		fd_table.left = new FormAttachment(btnStart, 0, SWT.LEFT);
		fd_table.right = new FormAttachment(100, -10);
		table.setLayoutData(fd_table);

		TableViewerColumn tblVwrClmnDistantUrl = new TableViewerColumn(tableViewer, SWT.NONE);
		tblclmnDistantUrl = tblVwrClmnDistantUrl.getColumn();
		tblclmnDistantUrl.setWidth(200);
		tblclmnDistantUrl.setText("Distant URL");

		TableViewerColumn tblVwrClmnLocalFile = new TableViewerColumn(tableViewer, SWT.NONE);
		tblVwrClmnLocalFile.setEditingSupport(new EditingSupport(tableViewer) {
			protected boolean canEdit(Object element) {
				return true;
			}
			protected CellEditor getCellEditor(Object element) {
				return new TextCellEditor(tableViewer.getTable());
			}
			protected Object getValue(Object element) {
				return ((Mapping)element).getLocalFilePath();
			}
			protected void setValue(Object element, Object value) {
				Mapping m = ((Mapping)element);
				String path = String.valueOf(value);
				m.setLocalFilePath(path);
				if (path == "") mapOfMappedMapping.remove(m.distantFileUrl);
				tableViewer.update(element, null);
			}
		});
		new TableViewerColumnSorter(tblVwrClmnLocalFile) {
			@Override
			protected int doCompare(Viewer viewer, Object e1, Object e2) {
				String l1 = ((Mapping)e1).localFilePath;
				String l2 = ((Mapping)e2).localFilePath;
				l1 = l1 == null?"":l1;
				l2 = l2 == null?"":l2;
				
				return l1.compareTo(l2);
			}
			@Override
			protected Object getValue(Object o) {
				// TODO remove this method, if your EditingSupport returns value
				return super.getValue(o);
			}
		};
		new TableViewerColumnSorter(tblVwrClmnDistantUrl) {
			@Override
			protected int doCompare(Viewer viewer, Object e1, Object e2) {
				String l1 = ((Mapping)e1).distantFileUrl;
				String l2 = ((Mapping)e2).distantFileUrl;
				l1 = l1 == null?"":l1;
				l2 = l2 == null?"":l2;
				
				return l1.compareTo(l2);
			}
			@Override
			protected Object getValue(Object o) {
				// TODO remove this method, if your EditingSupport returns value
				return super.getValue(o);
			}
		};
		TableColumn tblclmnLocalFile = tblVwrClmnLocalFile.getColumn();
		tblclmnLocalFile.setWidth(100);
		tblclmnLocalFile.setText("Local File");

		Label lblPort = new Label(parent, SWT.NONE);
		FormData fd_lblPort = new FormData();
		fd_lblPort.bottom = new FormAttachment(btnStart, 0, SWT.BOTTOM);
		lblPort.setLayoutData(fd_lblPort);
		lblPort.setText("port");

		txtPort = new Text(parent, SWT.BORDER);
		fd_lblPort.right = new FormAttachment(txtPort, -6);
		FormData fd_txtPort = new FormData();
		fd_txtPort.left = new FormAttachment(0, 108);
		fd_txtPort.bottom = new FormAttachment(btnStart, 0, SWT.BOTTOM);
		txtPort.setLayoutData(fd_txtPort);

		Label lblDomain = new Label(parent, SWT.NONE);
		FormData fd_lblDomain = new FormData();
		fd_lblDomain.bottom = new FormAttachment(btnStart, 0, SWT.BOTTOM);
		fd_lblDomain.left = new FormAttachment(txtPort, 40);
		lblDomain.setLayoutData(fd_lblDomain);
		lblDomain.setText("domain");

		txtDomain = new Text(parent, SWT.BORDER);
		FormData fd_txtUrl = new FormData();
		fd_txtUrl.right = new FormAttachment(100, -10);
		fd_txtUrl.left = new FormAttachment(lblDomain, 40);
		fd_txtUrl.top = new FormAttachment(0, 13);
		txtDomain.setLayoutData(fd_txtUrl);

		Label lblBaseFolder = new Label(parent, SWT.NONE);
		fd_table.top = new FormAttachment(lblBaseFolder, 6);
		FormData fd_lblBaseFolder = new FormData();
		fd_lblBaseFolder.top = new FormAttachment(lblDomain, 6);
		fd_lblBaseFolder.left = new FormAttachment(lblDomain, 0, SWT.LEFT);
		lblBaseFolder.setLayoutData(fd_lblBaseFolder);
		lblBaseFolder.setText("base folder");

		txtBaseFolder = new Text(parent, SWT.BORDER);
		FormData fd_txtBaseFolder = new FormData();
		fd_txtBaseFolder.top = new FormAttachment(txtDomain);
		fd_txtBaseFolder.right = new FormAttachment(table, 0, SWT.RIGHT);
		fd_txtBaseFolder.left = new FormAttachment(lblBaseFolder, 17);
		txtBaseFolder.setLayoutData(fd_txtBaseFolder);
		txtBaseFolder.addModifyListener(new ModifyListener() {
			
			@Override
			public void modifyText(ModifyEvent e) {
				baseFolder = ((Text)e.getSource()).getText();
			}
		});
		initDataBindings();
		txtPort.setText("50008");
		if (memento != null) {
			Integer memPort = memento.getInteger("txtPort");
			txtPort.setText(memPort != null?memPort.toString():"50008");
			String memBaseFolder = memento.getString("txtBaseFolder");
			txtBaseFolder.setText(memBaseFolder != null?memBaseFolder:"");
			String memDomain = memento.getString("txtDomain");
			txtDomain.setText(memDomain != null?memDomain:"");

			String memMappings = memento.getString("mappingsString");
			if (memMappings != null && memMappings.length() > 0) {
				String[] mappingsToRestore = memMappings.split("\\|");
				Vector<Mapping> mappingRestored = new Vector<>();
				for(String m : mappingsToRestore) {
					if (m.length() == 0) continue;
					int posComma = m.indexOf(",");
					String url = m.substring(0,posComma);
					String path = m.substring(posComma + 1);
					mappingRestored.add(new Mapping(url, path));
				}
				addToMapping(mappingRestored);
			}
			baseFolder = txtBaseFolder.getText();
			if (memento.getInteger("tblclmnDistantUrlWidth") != null) {
				tblclmnDistantUrl.setWidth(memento.getInteger("tblclmnDistantUrlWidth"));
			}
		}

		final ServerMainSocket server = new ServerMainSocket(Integer.parseInt(txtPort.getText()), mapOfMappedMapping);
		server.setDomain(txtDomain.getText());
		
		server.setChildListener(new AddChildAdapter() {
			@Override
			public void addFileUrl(final String url) {
				Display.getDefault().asyncExec(new Runnable() {

					@Override
					public void run() {
						synchronized (urlsToAdd) {
							urlsToAdd.add(url);
							if (urlsToAdd.size() == 1) {
								Display.getDefault().asyncExec(addMappingToAddRunnable);
							}	
						}
					}
				});
			}
		});
		
		btnStart.addMouseListener(new MouseAdapter() {

			Thread w;

			@Override
			public void mouseDown(MouseEvent e) {
				if (running == false) {
					server.setPort(Integer.parseInt(txtPort.getText()));
					running = true;
					btnStart.setText("Stop");
					server.setToBreak(false);
					txtPort.setEnabled(false);
					w = new Thread(server);
					w.setName(server.toString());
					w.start();
				} else {
					server.setToBreak(true);
					w.interrupt();

					running = false;
					btnStart.setText("Start");
					txtPort.setEnabled(true);
					while (w.isAlive()) {
						try {
							Thread.sleep(500);
						} catch (InterruptedException e1) {
							// TODO Auto-generated catch block
							e1.printStackTrace();
						}
					}
				}
				// addEndPoint("Test"+Math.random());
			}
		});
	}

	/**
	 * Passing the focus request to the viewer's control.
	 */
	public void setFocus() {
		tableViewer.getControl().setFocus();
	}
	protected DataBindingContext initDataBindings() {
		DataBindingContext bindingContext = new DataBindingContext();
		//
		ObservableListContentProvider listContentProvider = new ObservableListContentProvider();
		IObservableMap[] observeMaps = PojoObservables.observeMaps(listContentProvider.getKnownElements(), Mapping.class, new String[]{"distantFileUrl", "localFilePath"});
		tableViewer.setLabelProvider(new ObservableMapLabelProvider(observeMaps));
		tableViewer.setContentProvider(listContentProvider);
		//
		tableViewer.setInput(mappings);
		//
		return bindingContext;
	}

	@Override
	public void init(IViewSite site, IMemento memento) throws PartInitException {
		this.memento = memento;

		super.init(site, memento);
	}

	List<Mapping> getValidMappings() {
		Object ms[] = mappings.toArray();
		List<Mapping> validMappings = new ArrayList<>();
		for(Object mo : ms) {
			Mapping m = (Mapping)mo;
			if (m.localFilePath != null && m.localFilePath.length() > 0	&& 
					m.distantFileUrl != null && m.distantFileUrl.length() > 0) {
				validMappings.add(m);
			}
		}
		return validMappings;
	} 
	
	@Override
	public void saveState(IMemento memento) {
		memento.putInteger("txtPort", Integer.parseInt(txtPort.getText()));
		memento.putString("txtBaseFolder", txtBaseFolder.getText());
		memento.putString("txtDomain", txtDomain.getText());

		Object ms[] = mappings.toArray();
		StringBuffer mappingsString = new StringBuffer(512);
		for(Object mo : ms) {
			Mapping m = (Mapping)mo;
			if (m.localFilePath != null && m.localFilePath.length() > 0	&& 
					m.distantFileUrl != null && m.distantFileUrl.length() > 0) {
				mappingsString.append("|"+m.distantFileUrl+","+m.localFilePath);
			}
		}

		memento.putString("mappingsString", mappingsString.toString());

		memento.putInteger("tblclmnDistantUrlWidth", tblclmnDistantUrl.getWidth());

		super.saveState(memento);
	}
}