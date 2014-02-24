/*
 * $Id$
 *
 * This file is part of the DecoJer project.
 * Copyright (C) 2010-2011  André Pankraz
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.

 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * In accordance with Section 7(b) of the GNU Affero General Public License,
 * a covered work must retain the producer line in every Java Source Code
 * that is created using DecoJer.
 */
package org.decojer.editor.eclipse.cfg;

import java.util.IdentityHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.decojer.cavaj.model.BD;
import org.decojer.cavaj.model.D;
import org.decojer.cavaj.model.code.BB;
import org.decojer.cavaj.model.code.CFG;
import org.decojer.cavaj.model.code.E;
import org.decojer.cavaj.model.methods.MD;
import org.decojer.cavaj.model.types.TD;
import org.decojer.cavaj.transformers.TrCalculatePostorder;
import org.eclipse.draw2d.ColorConstants;
import org.eclipse.draw2d.Label;
import org.eclipse.draw2d.Polyline;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.zest.core.widgets.Graph;
import org.eclipse.zest.core.widgets.GraphConnection;
import org.eclipse.zest.core.widgets.GraphNode;
import org.eclipse.zest.core.widgets.ZestStyles;
import org.eclipse.zest.layouts.LayoutStyles;

/**
 * CFG Viewer.
 * 
 * @author André Pankraz
 */
public class CfgViewer extends Composite {

	private final static Logger LOGGER = Logger.getLogger(CfgViewer.class.getName());

	private final Button cfgAntialiasingCheckbox;

	private final Combo cfgViewModeCombo;

	private final Graph graph;

	private D selectedD;

	/**
	 * Constructor.
	 * 
	 * @param parent
	 *            parent composite
	 * @param style
	 *            component style
	 */
	public CfgViewer(final Composite parent, final int style) {
		super(parent, style);
		final GridLayout layout = new GridLayout(2, false);
		setLayout(layout);

		this.cfgAntialiasingCheckbox = new Button(this, SWT.CHECK);
		GridData gridData = new GridData();
		this.cfgAntialiasingCheckbox.setLayoutData(gridData);
		this.cfgAntialiasingCheckbox.setText("Antialiasing");
		this.cfgAntialiasingCheckbox.addSelectionListener(new SelectionListener() {

			@Override
			public void widgetDefaultSelected(final SelectionEvent e) {
				initGraph();
			}

			@Override
			public void widgetSelected(final SelectionEvent e) {
				initGraph();
			}

		});
		this.cfgViewModeCombo = new Combo(this, SWT.READ_ONLY);
		this.cfgViewModeCombo.setItems(new String[] { "Data Flow Analysis", "Java Expressions",
				"Control Flow Analysis", "Control Flow Statements" });
		this.cfgViewModeCombo.setText("Control Flow Statements");
		this.cfgViewModeCombo.addSelectionListener(new SelectionListener() {

			@Override
			public void widgetDefaultSelected(final SelectionEvent e) {
				initGraph();
			}

			@Override
			public void widgetSelected(final SelectionEvent e) {
				initGraph();
			}

		});
		gridData = new GridData();
		this.cfgViewModeCombo.setLayoutData(gridData);
		// draw graph
		// Graph will hold all other objects
		this.graph = new Graph(this, SWT.NONE);
		gridData = new GridData();
		gridData.horizontalSpan = 2;
		gridData.horizontalAlignment = GridData.FILL;
		gridData.grabExcessHorizontalSpace = true;
		gridData.verticalAlignment = GridData.FILL;
		gridData.grabExcessVerticalSpace = true;
		this.graph.setLayoutData(gridData);
		this.graph.setLayoutAlgorithm(new HierarchicalLayoutAlgorithm(
				LayoutStyles.NO_LAYOUT_NODE_RESIZING), true);
	}

	private GraphNode addToGraph(final BB bb, final IdentityHashMap<BB, GraphNode> map) {
		final GraphNode node = new GraphNode(this.graph, SWT.NONE, bb.toString(), bb);
		if (bb.getStruct() != null) {
			node.setTooltip(new Label(bb.getStruct().toString()));
		} else if (bb.getCfg().isFrames()) {
			node.setTooltip(new FramesFigure(bb));
		} else {
			node.setTooltip(null);
		}
		map.put(bb, node);

		for (final E out : bb.getOuts()) {
			GraphNode succNode = map.get(out.getEnd());
			if (succNode == null) {
				succNode = addToGraph(out.getEnd(), map);
			}
			final GraphConnection connection = new GraphConnection(this.graph,
					ZestStyles.CONNECTIONS_DIRECTED, node, succNode);
			if (this.cfgAntialiasingCheckbox.getSelection()) {
				((Polyline) connection.getConnectionFigure()).setAntialias(SWT.ON);
			}
			connection.setText(out.getValueString());
			if (out.isBack()) {
				connection.setCurveDepth(50);
				connection.setLineColor(ColorConstants.red);
			} else if (out.isCatch()) {
				connection.setLineColor(ColorConstants.yellow);
			}
		}
		return node;
	}

	public void initGraph() {
		CFG cfg = null;
		if (this.selectedD instanceof MD) {
			cfg = ((MD) this.selectedD).getCfg();
		} else if (this.selectedD instanceof TD) {
			for (final BD bd : ((TD) this.selectedD).getBds()) {
				if (bd instanceof MD && ((MD) bd).isConstructor()) {
					cfg = ((MD) bd).getCfg();
					if (cfg != null) {
						break;
					}
				}
			}
		} else {
			return;
		}
		if (cfg == null) {
			return;
		}
		try {
			final int stage = this.cfgViewModeCombo.getSelectionIndex();
			cfg.decompile(stage);
		} catch (final Throwable e) {
			TrCalculatePostorder.transform(cfg);
			LOGGER.log(Level.WARNING, "Cannot transform '" + cfg + "'!", e);
		}
		initGraph(cfg);
	}

	private void initGraph(final CFG cfg) {
		// dispose old graph content, first connections than nodes
		Object[] objects = this.graph.getConnections().toArray();
		for (final Object object : objects) {
			((GraphConnection) object).dispose();
		}
		objects = this.graph.getNodes().toArray();
		for (final Object object : objects) {
			((GraphNode) object).dispose();
		}
		// add graph content
		addToGraph(cfg.getStartBb(), new IdentityHashMap<BB, GraphNode>());
		this.graph.applyLayout();
	}

	/**
	 * Select declaration.
	 * 
	 * @param selectedD
	 *            selected declaration
	 */
	public void setlectD(final D selectedD) {
		this.selectedD = selectedD;
		initGraph();
	}

}