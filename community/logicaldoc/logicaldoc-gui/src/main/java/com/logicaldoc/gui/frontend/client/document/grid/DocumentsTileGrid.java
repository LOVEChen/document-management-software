package com.logicaldoc.gui.frontend.client.document.grid;

import com.logicaldoc.gui.common.client.Constants;
import com.logicaldoc.gui.common.client.Session;
import com.logicaldoc.gui.common.client.beans.GUIDocument;
import com.logicaldoc.gui.common.client.beans.GUIFolder;
import com.logicaldoc.gui.common.client.i18n.I18N;
import com.logicaldoc.gui.common.client.observer.DocumentController;
import com.logicaldoc.gui.common.client.observer.DocumentObserver;
import com.logicaldoc.gui.common.client.util.DocUtil;
import com.logicaldoc.gui.common.client.util.DocumentProtectionManager;
import com.logicaldoc.gui.common.client.util.DocumentProtectionManager.DocumentProtectionHandler;
import com.logicaldoc.gui.common.client.util.Util;
import com.smartgwt.client.data.AdvancedCriteria;
import com.smartgwt.client.data.DataSource;
import com.smartgwt.client.data.Record;
import com.smartgwt.client.data.RecordList;
import com.smartgwt.client.types.OperatorId;
import com.smartgwt.client.types.SelectionStyle;
import com.smartgwt.client.widgets.Canvas;
import com.smartgwt.client.widgets.events.DoubleClickEvent;
import com.smartgwt.client.widgets.events.DoubleClickHandler;
import com.smartgwt.client.widgets.events.ShowContextMenuEvent;
import com.smartgwt.client.widgets.events.ShowContextMenuHandler;
import com.smartgwt.client.widgets.grid.events.CellContextClickHandler;
import com.smartgwt.client.widgets.grid.events.DataArrivedHandler;
import com.smartgwt.client.widgets.grid.events.SelectionChangedHandler;
import com.smartgwt.client.widgets.tile.TileGrid;
import com.smartgwt.client.widgets.tile.events.DataArrivedEvent;
import com.smartgwt.client.widgets.tile.events.SelectionChangedEvent;
import com.smartgwt.client.widgets.viewer.DetailFormatter;
import com.smartgwt.client.widgets.viewer.DetailViewerField;

/**
 * Grid used to show a documents gallery during navigation or searches.
 * 
 * @author Marco Meschieri - Logical Objects
 * @since 7.0
 */
public class DocumentsTileGrid extends TileGrid implements DocumentsGrid, DocumentObserver {
	private Cursor cursor;

	private GUIFolder folder = null;

	public DocumentsTileGrid(GUIFolder folder, final DataSource ds, final int totalRecords) {
		this.folder = folder;
		setTileWidth(200);
		setTileHeight(250);
		setAutoFetchData(true);
		setSelectionType(SelectionStyle.MULTIPLE);
		setShowAllRecords(false);
		setCanReorderTiles(false);
		setWidth100();

		DetailViewerField thumbnail = new DetailViewerField("thumbnail");
		thumbnail.setDetailFormatter(new DetailFormatter() {

			@Override
			public String format(Object value, Record record, DetailViewerField field) {
				int tileSize = 200;
				if (Session.get().getConfig("gui.tile.size") != null)
					tileSize = Integer.parseInt(Session.get().getConfig("gui.tile.size"));

				try {
					if ("folder".equals(record.getAttribute("type")))
						return Util.imageHTML("folder_tile.png", null, tileSize, null);
					else {
						long docId = Long.parseLong(record.getAttribute("id"));
						if (!record.getAttributeAsBoolean("password") || DocumentProtectionManager.isUnprotected(docId))
							return Util.tileImageHTML(docId, null, null, tileSize);
						else
							return Util.imageHTML("blank.png", null, "width:" + tileSize + "px height:" + tileSize
									+ "px");
					}
				} catch (Throwable e) {
					return "";
				}
			}
		});

		DetailViewerField filename = new DetailViewerField("filename");
		filename.setDetailFormatter(new DetailFormatter() {

			@Override
			public String format(Object value, Record record, DetailViewerField field) {
				try {
					String html = "<table style='margin-top:2px' align='center' border='0' cellspacing='0'>";

					// The title row
					html += "<tr><td>" + Util.imageHTML(record.getAttribute("icon") + ".png") + "</td><td>" + value
							+ "</td></tr></table>";
					html += "<table align='center' border='0' cellspacing='0'><tr>";

					// The status row
					html += "<td>"
							+ Util.imageHTML(DocUtil.getBookmarkedIcon(record.getAttributeAsBoolean("bookmarked")))
							+ "</td>";
					html += "<td>" + Util.imageHTML(DocUtil.getIndexedIcon(record.getAttributeAsInt("indexed")))
							+ "</td>";

					// The locked icon
					if (record.getAttribute("status") != null) {
						Integer status = record.getAttributeAsInt("status");
						if (status != null && status.intValue() > 0) {
							String alt = null;
							if (status == Constants.DOC_CHECKED_OUT || status == Constants.DOC_LOCKED)
								alt = I18N.message("lockedby") + " " + record.getAttributeAsString("lockUser");
							html += "<td>" + Util.imageHTML(DocUtil.getLockedIcon(status), alt, null) + "</td>";
						}
					}

					html += "<td>"
							+ Util.imageHTML(DocUtil.getPasswordProtectedIcon(record.getAttributeAsBoolean("password")))
							+ "</td>";
					html += "<td>" + Util.imageHTML(DocUtil.getImmutableIcon(record.getAttributeAsInt("immutable")))
							+ "</td>";
					html += "<td>" + Util.imageHTML(DocUtil.getSignedIcon(record.getAttributeAsInt("signed")))
							+ "</td>";
					html += "<td>" + Util.imageHTML(DocUtil.getStampedIcon(record.getAttributeAsInt("stamped")))
							+ "</td>";
					html += "</tr></table>";

					return html;
				} catch (Throwable e) {
					return "";
				}
			}
		});

		setFields(thumbnail, filename);

		if (ds == null) {
			/*
			 * We are searching
			 */
			setSelectionType(SelectionStyle.SINGLE);
		} else {
			setDataSource(ds);
		}

		addDataArrivedHandler(new com.smartgwt.client.widgets.tile.events.DataArrivedHandler() {

			@Override
			public void onDataArrived(DataArrivedEvent event) {
				if (cursor != null) {
					cursor.setMessage(I18N.message("showndocuments", Integer.toString(getCount())));
					cursor.setTotalRecords(totalRecords);
				}

				sortByProperty("filename", true);
			}
		});

		DocumentController.get().addObserver(this);
	}

	@Override
	public void updateDocument(GUIDocument document) {
		Record record = findRecord(document.getId());
		if (record != null) {
			GridUtil.updateRecord(document, record);
			Canvas tile = getTile(record);
			tile.redraw();
		}
	}

	@Override
	public void setDocuments(GUIDocument[] documents) {
		Record[] records = new Record[0];
		if (documents == null || documents.length == 0)
			setData(records);

		records = new Record[documents.length];
		for (int i = 0; i < documents.length; i++) {
			GUIDocument doc = documents[i];
			Record record = GridUtil.fromDocument(doc);
			records[i] = record;
		}

		setData(records);
	}

	@Override
	public GUIDocument getSelectedDocument() {
		return GridUtil.toDocument(getSelectedRecord());
	}

	@Override
	public GUIDocument[] getSelectedDocuments() {
		return GridUtil.toDocuments(getSelection());
	}

	@Override
	public GUIDocument[] getDocuments() {
		return GridUtil.toDocuments(getRecordList().toArray());
	}

	@Override
	public int getSelectedIndex() {
		return super.getRecordIndex(getSelectedRecord());
	}

	@Override
	public long[] getSelectedIds() {
		return GridUtil.getIds(getSelection());
	}

	@Override
	public long[] getIds() {
		return GridUtil.getIds(getRecordList().toArray());
	}

	@Override
	public void deselectAll() {
		deselectAllRecords();
	}

	@Override
	public void setCanExpandRows() {
		// Nothing to do
	}

	@Override
	public int getCount() {
		RecordList rl = getRecordList();
		if (rl != null)
			return getRecordList().getLength();
		else
			return 0;
	}

	@Override
	public int getSelectedCount() {
		Record[] selection = getSelection();
		if (selection != null)
			return selection.length;
		else
			return 0;
	}

	@Override
	public void showFilters(boolean showFilters) {
		// Nothing to do
	}

	@Override
	public void selectDocument(long docId) {
		deselectAll();
		RecordList rlist = getDataAsRecordList();
		Record record = rlist.find("id", Long.toString(docId));
		if (record != null)
			selectRecord(record);
	}

	@Override
	public void expandVisibleRows() {
		// Nothing to do
	}

	@Override
	public void setCanDrag(boolean drag) {
		super.setCanDrag(drag);
		setCanDragTilesOut(drag);
	}

	@Override
	public void registerDoubleClickHandler(final DoubleClickHandler handler) {
		addDoubleClickHandler(new DoubleClickHandler() {

			@Override
			public void onDoubleClick(DoubleClickEvent event) {
				GUIDocument selectedDocument = getSelectedDocument();
				if (selectedDocument == null)
					return;
				DocumentProtectionManager.askForPassword(selectedDocument.getId(), new DocumentProtectionHandler() {
					@Override
					public void onUnprotected(GUIDocument document) {
						handler.onDoubleClick(null);
					}
				});
			}
		});
	}

	@Override
	public void registerSelectionChangedHandler(final SelectionChangedHandler handler) {
		addSelectionChangedHandler(new com.smartgwt.client.widgets.tile.events.SelectionChangedHandler() {

			@Override
			public void onSelectionChanged(SelectionChangedEvent event) {
				GUIDocument selectedDocument = getSelectedDocument();
				if (selectedDocument == null)
					return;
				DocumentProtectionManager.askForPassword(selectedDocument.getId(), new DocumentProtectionHandler() {
					@Override
					public void onUnprotected(GUIDocument document) {
						handler.onSelectionChanged(null);
					}
				});
			}
		});
	}

	@Override
	public void registerCellContextClickHandler(final CellContextClickHandler handler) {
		addShowContextMenuHandler(new ShowContextMenuHandler() {

			@Override
			public void onShowContextMenu(final ShowContextMenuEvent event) {
				GUIDocument selectedDocument = getSelectedDocument();
				if (selectedDocument == null)
					return;
				DocumentProtectionManager.askForPassword(selectedDocument.getId(), new DocumentProtectionHandler() {
					@Override
					public void onUnprotected(GUIDocument document) {
						handler.onCellContextClick(null);
					}
				});
				if (event != null)
					event.cancel();
			}
		});
	}

	@Override
	public void registerDataArrivedHandler(final DataArrivedHandler handler) {
		addDataArrivedHandler(new com.smartgwt.client.widgets.tile.events.DataArrivedHandler() {

			@Override
			public void onDataArrived(DataArrivedEvent event) {
				handler.onDataArrived(null);
			}
		});
	}

	@Override
	public void removeSelectedDocuments() {
		removeSelectedData();
	}

	@Override
	public void setCursor(Cursor cursor) {
		this.cursor = cursor;
	}

	@Override
	public void onDocumentSelected(GUIDocument document) {
	}

	@Override
	public void onDocumentModified(GUIDocument document) {
		updateDocument(document);
	}

	@Override
	public void onDocumentStored(GUIDocument document) {
		if (folder != null && document.getFolder().getId() == folder.getId()) {
			Record doc = findRecord(document.getId());
			if (doc != null)
				return;

			addData(GridUtil.fromDocument(document));
			cursor.setMessage(I18N.message("showndocuments", Integer.toString(getData().length)));
		}
	}

	@Override
	public void onDocumentsDeleted(GUIDocument[] documents) {
		for (GUIDocument doc : documents) {
			Record record = findRecord(doc.getId());
			if (record != null) {
				try {
					removeData(record);
					cursor.setMessage(I18N.message("showndocuments",
							"" + (getData() != null ? Integer.toString(getData().length) : 0)));
				} catch (Throwable t) {

				}
			}
		}
	}

	@Override
	public void onDocumentCheckedIn(GUIDocument document) {
		onDocumentModified(document);
	}

	@Override
	public void onDocumentCheckedOut(GUIDocument document) {
		onDocumentModified(document);
	}

	@Override
	public void onDocumentLocked(GUIDocument document) {
		onDocumentModified(document);
	}

	@Override
	public void onDocumentUnlocked(GUIDocument document) {
		onDocumentModified(document);
	}

	@Override
	public void onDocumentMoved(GUIDocument document) {
		if (folder != null && document.getFolder().getId() != folder.getId())
			onDocumentsDeleted(new GUIDocument[] { document });
	}

	private Record findRecord(long docId) {
		Record record = find(new AdvancedCriteria("id", OperatorId.EQUALS, docId));
		if (record == null)
			record = find(new AdvancedCriteria("docref", OperatorId.EQUALS, docId));
		return record;
	}

	@Override
	public void destroy() {
		DocumentController.get().removeObserver(this);
		super.destroy();
	}

	@Override
	protected void finalize() {
		destroy();
	}
	
	@Override
	public GUIFolder getFolder() {
		return folder;
	}
}