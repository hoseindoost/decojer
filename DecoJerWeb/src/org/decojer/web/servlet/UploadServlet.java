/*
 * $Id$
 *
 * This file is part of the DecoJer project.
 * Copyright (C) 2010-2011  Andr� Pankraz
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
package org.decojer.web.servlet;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.decojer.DecoJer;
import org.decojer.PackageClassStreamProvider;
import org.decojer.cavaj.model.CU;
import org.decojer.cavaj.model.PF;
import org.decojer.cavaj.model.TD;
import org.decojer.web.analyser.AnalyseException;
import org.decojer.web.analyser.BlobAnalyser;
import org.decojer.web.analyser.BlobInfo;
import org.decojer.web.analyser.ClassAnalyser;
import org.decojer.web.analyser.DexAnalyser;
import org.decojer.web.analyser.DexInfo;
import org.decojer.web.analyser.JarAnalyser;
import org.decojer.web.analyser.JarInfo;
import org.decojer.web.analyser.TypeInfo;
import org.decojer.web.util.EntityConstants;
import org.decojer.web.util.Messages;

import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.blobstore.BlobstoreInputStream;
import com.google.appengine.api.blobstore.BlobstoreService;
import com.google.appengine.api.blobstore.BlobstoreServiceFactory;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;

/**
 * @author Andr� Pankraz
 */
public class UploadServlet extends HttpServlet {

	private static Logger LOGGER = Logger.getLogger(UploadServlet.class
			.getName());

	private static final long serialVersionUID = -6567596163814017159L;

	private final BlobstoreService blobstoreService = BlobstoreServiceFactory
			.getBlobstoreService();

	private final DatastoreService datastoreService = DatastoreServiceFactory
			.getDatastoreService();

	@Override
	public void doPost(final HttpServletRequest req,
			final HttpServletResponse res) throws ServletException, IOException {
		// get and check BlobKey
		final Map<String, BlobKey> blobs = this.blobstoreService
				.getUploadedBlobs(req);
		BlobKey blobKey = blobs.get("file");
		if (blobKey == null) {
			Messages.addMessage(req, "File was empty!");
			res.sendRedirect("/");
			return;
		}
		final HashSet<BlobKey> deleteBlobKeys = new HashSet<BlobKey>();
		try {
			// read blob meta data for upload and find all duplicates;
			// attention: this servlet can rely on the existence of the current
			// uploads blob meta data via datastoreService.get(), but the
			// results from other queries are HA write lag dependend!
			final BlobInfo blobInfo = BlobAnalyser.analyse(
					this.datastoreService, blobKey);
			blobKey = blobInfo.blobKey;
			if (!blobInfo.deleteBlobKeys.isEmpty()) {
				deleteBlobKeys.addAll(blobInfo.deleteBlobKeys);
				Messages.addMessage(req, "Duplicate! Delete "
						+ blobInfo.deleteBlobKeys.size() + " entries!");
			}
			// read CLASS and DEX streams directly into a byte array, the
			// used readers do this anyway but with an available() sized buffer,
			// which is 0 for BlobstoreInputStream!
			if (blobInfo.kind == EntityConstants.KIND_CLASS) {
				final TypeInfo typeInfo;
				final byte[] bytes = this.blobstoreService.fetchData(blobKey,
						0, blobInfo.size);
				try {
					typeInfo = ClassAnalyser.analyse(bytes);
					// class:
					// org/decojer/cavaj/test/jdk6/DecTestParametrizedMethods
					// fileName: DecTestParametrizedMethods.class
				} catch (final Exception e) {
					throw new AnalyseException(
							"This isn't a valid Java Class like the file extension suggests.");
				}
				// update class entity
				final Key uploadKey = blobInfo.createKey();
				Entity uploadEntity;
				try {
					uploadEntity = this.datastoreService.get(uploadKey);
				} catch (final EntityNotFoundException e) {
					uploadEntity = blobInfo.createEntity(uploadKey);
				}
				final Integer uploads = (Integer) uploadEntity
						.getProperty(EntityConstants.PROP_UPLOADS);
				uploadEntity.setUnindexedProperty(EntityConstants.PROP_UPLOADS,
						uploads == null ? deleteBlobKeys.size() : uploads
								+ deleteBlobKeys.size());
				uploadEntity.setUnindexedProperty(EntityConstants.PROP_NEWEST,
						blobInfo.newestDate);
				this.datastoreService.put(uploadEntity);
				// update type entity
				final Key typeKey = typeInfo.createKey();
				Entity typeEntity;
				try {
					typeEntity = this.datastoreService.get(typeKey);
				} catch (final EntityNotFoundException e) {
					typeEntity = typeInfo.createEntity(typeKey);
					this.datastoreService.put(typeEntity);
				}
				final Key memberKey = KeyFactory.createKey(typeKey,
						EntityConstants.KIND_MEMBER, blobInfo.md5Hash
								+ blobInfo.size);
				Entity memberEntity;
				try {
					memberEntity = this.datastoreService.get(memberKey);
				} catch (final EntityNotFoundException e) {
					memberEntity = new Entity(memberKey);
					memberEntity.setUnindexedProperty(
							EntityConstants.PROP_UPLOAD, uploadKey);
					this.datastoreService.put(uploadEntity);
				}
				// TODO delete here
				try {
					try {
						final PackageClassStreamProvider packageClassStreamProvider = new PackageClassStreamProvider(
								null);
						packageClassStreamProvider.addClassStream("TEST",
								new ByteArrayInputStream(bytes));
						final PF pf = DecoJer
								.createPF(packageClassStreamProvider);
						final Entry<String, TD> next = pf.getTds().entrySet()
								.iterator().next();
						final CU cu = DecoJer.createCU(next.getValue());
						final String source = DecoJer.decompile(cu);
						Messages.addMessage(req,
								"This is currently only a pre-alpha test-version!");
						Messages.addMessage(req, "<pre>" + source + "</pre>");
					} catch (final Exception e) {
						LOGGER.log(Level.WARNING,
								"Problems with decompilation.", e);
						Messages.addMessage(req, "Decompilation problems!");
						res.sendRedirect("/");
						return;
					}
				} catch (final Exception e) {
					LOGGER.log(Level.WARNING, "Problems with stream visitor.",
							e);
					Messages.addMessage(req, "Internal system problem!");
					res.sendRedirect("/");
					return;
				}
			} else if (blobInfo.kind == EntityConstants.KIND_JAR) {
				final JarInfo jarInfo;
				try {
					jarInfo = JarAnalyser.analyse(new BlobstoreInputStream(
							blobKey));
				} catch (final Exception e) {
					throw new AnalyseException(
							"This isn't a valid Java Class like the file extension suggests.");
				}
				LOGGER.info("JAR Check failures: " + jarInfo.checkFailures);
				if (jarInfo.typeInfos.size() == 0) {
					throw new AnalyseException(
							"This isn't a valid Java Archive like the file extension suggests.");
				}
				// 2011-07-09 05:02:03.668 /upload 302 30443ms 1757400cpu_ms
				// 1747537api_cpu_ms 0kb Mozilla/5.0 (Windows NT 6.0; rv:5.0)
				// Gecko/20100101 Firefox/5.0,gzip(gfe),gzip(gfe),gzip(gfe)
				// if (!duplicateBlobKeys.isEmpty()) {
				// 4.3 MB JAR => 290 s, Rewrite 180 s,
				// following batch get costs 12 s and reduces rewrite to 15 s,
				// with single gets and Key-Only-Queries reduced to 18 s
				Messages.addMessage(req, "Check uploaded Java classes: "
						+ jarInfo.typeInfos.size());
				final List<Entity> typeEntities = new ArrayList<Entity>();
				for (final TypeInfo typeInfo : jarInfo.typeInfos) {
					typeEntities
							.add(typeInfo.createEntity(typeInfo.createKey()));
				}
				// final Map<Key, Entity> map = this.datastoreService
				// .get(typeKeys);
				// streamAnalyzer.classEntities.removeAll(map.values());
				// }
				// 20 MB EAR with 9886 Classes
				// 29.29 min!
				// only 10 seconds not in API stuff
				// without binary 15 minutes:
				// 10 entities pro CPU second, 1.000.000 => 27 CPU hours
				final List<Key> put = this.datastoreService.put(typeEntities);
				Messages.addMessage(req, "Updated Java classes: "
						+ typeEntities.size());
				Messages.addMessage(req, jarInfo.typeInfos.size()
						+ " Classes found. Sry not ready yet!");
				res.sendRedirect("/");
				return;
			} else if (blobInfo.kind == EntityConstants.KIND_DEX) {
				final DexInfo dexInfo;
				final byte[] bytes = this.blobstoreService.fetchData(blobKey,
						0, blobInfo.size);
				try {
					dexInfo = DexAnalyser.analyse(bytes);
				} catch (final Exception e) {
					throw new AnalyseException(
							"This isn't a valid Android / Dalvik Executable like the file extension suggests.");
				}
				Messages.addMessage(req, dexInfo.typeInfos.size()
						+ " Classes found. Sry not ready yet!");
				res.sendRedirect("/");
				return;
			}
		} catch (final AnalyseException e) {
			LOGGER.log(Level.INFO, e.getMessage());
			Messages.addMessage(
					req,
					e.getMessage()
							+ "  Please upload valid Java Classes or Archives (JAR) respectively Android / Dalvik Executable File (DEX).");
			res.sendRedirect("/");
			deleteBlobKeys.add(blobKey);
			return;
		} catch (final Exception e) {
			LOGGER.log(Level.WARNING,
					"Unexpected problem, couldn't evaluate upload: " + blobKey,
					e);
			Messages.addMessage(req,
					"Unexpected problem, couldn't evaluate upload!");
			res.sendRedirect("/");
			// deleteBlobKeys.add(blobKey);
			return;
		} finally {
			LOGGER.info("Deleting '" + deleteBlobKeys.size() + "' uploads.");
			this.blobstoreService.delete(deleteBlobKeys
					.toArray(new BlobKey[deleteBlobKeys.size()]));
		}
		res.sendRedirect("/");
	}
}