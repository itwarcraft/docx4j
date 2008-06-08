/*
 *  Copyright 2007-2008, Plutext Pty Ltd.
 *   
 *  This file is part of docx4j.

    docx4j is licensed under the Apache License, Version 2.0 (the "License"); 
    you may not use this file except in compliance with the License. 

    You may obtain a copy of the License at 

        http://www.apache.org/licenses/LICENSE-2.0 

    Unless required by applicable law or agreed to in writing, software 
    distributed under the License is distributed on an "AS IS" BASIS, 
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
    See the License for the specific language governing permissions and 
    limitations under the License.

 */

package org.docx4j.openpackaging.io;



import java.io.InputStream;
import java.io.IOException;

import java.util.Iterator;

import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.Property;
import javax.jcr.Repository;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.jcr.version.*;
import javax.jcr.NodeIterator;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import javax.xml.bind.JAXBException;
import javax.xml.parsers.ParserConfigurationException;

//import org.apache.jackrabbit.core.TransientRepository;  // Testing only

import org.apache.log4j.Logger;

import org.docx4j.JcrNodeMapper.NodeMapper;
import org.docx4j.jaxb.Context;
import org.docx4j.openpackaging.URIHelper;
import org.docx4j.openpackaging.contenttype.ContentTypeManager;
import org.docx4j.openpackaging.exceptions.InvalidFormatException;
import org.docx4j.openpackaging.packages.Package;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.Part;
import org.docx4j.openpackaging.parts.PartName;
import org.docx4j.openpackaging.parts.WordprocessingML.BinaryPart;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.openpackaging.parts.relationships.Relationship;
import org.docx4j.openpackaging.parts.relationships.RelationshipsPart;
import org.docx4j.openpackaging.parts.relationships.TargetMode;

import org.docx4j.openpackaging.exceptions.Docx4JException;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;

import org.merlin.io.DOMSerializerEngine;
import org.merlin.io.OutputEngineInputStream;

import java.util.Calendar;

/**
 * Save a Package object to the JCR
 * 
 * However, note that this class doesn't save as a single
 * zipped up node in JCR - only as discrete unzipped
 * parts (ie a JCR node for each part).
 * 
 * @author jharrop
 *
 */
public class SaveToJCR {
	
	private static Logger log = Logger.getLogger(SaveToJCR.class);		

	
	public SaveToJCR(Package p, NodeMapper nodeMapper, Session jcrSession) {
		
		this.p = p;
		this.jcrSession = jcrSession;
		this.nodeMapper = nodeMapper;
		
	}
	
	public static final String CM_NAMESPACE = "cm:";
	
	// The package to save
	public Package p;
			
	public Session jcrSession;
	
	public NodeMapper nodeMapper = null;
	
	
	/* Save a Package to baseNode in JCR.
	 * baseNode must exist an be a suitable node type.             
	 * eg Node exampledoc1 = docs.addNode("exampledoc1.docx", "nt:file");            
          Node baseNode = exampledoc1.addNode("jcr:content", "nt:unstructured");
       Consider passing instead the folder node and desired filename.
	 *  */
	public boolean save(Node baseNode) throws Docx4JException {
		
		
		 try {
			log.info("Saving to" +  baseNode.getPath() );
			
//	        // Create the ZIP file
//	        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(filepath));

			// 3. Get [Content_Types].xml
			ContentTypeManager ctm = p.getContentTypeManager();
			org.w3c.dom.Document w3cDoc = null;
			// convert dom4j node to real W3C dom node
			w3cDoc = new org.dom4j.io.DOMWriter()
					.write(ctm.getDocument());
			saveRawXmlPart(baseNode, "[Content_Types].xml", w3cDoc );
	        
			// 4. Start with _rels/.rels

//			<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
//			  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/>
//			  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
//			  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
//			</Relationships>		
			
			String partName = "_rels/.rels";
			RelationshipsPart rp = p.getRelationshipsPart();
			// TODO - replace with saveRawXmlPart(baseNode, rp)
			// once we know that partName resolves correctly
			saveRawXmlPart(baseNode, partName, rp.getW3cDocument() );
			
			
			// 5. Now recursively 
//			addPartsFromRelationships(baseNode, "", rp );
			addPartsFromRelationships(baseNode, rp );

			jcrSession.save();
	    
	    } catch (Exception e) {
			e.printStackTrace() ;
			if (e instanceof Docx4JException) {
				throw (Docx4JException)e;
			} else {
				throw new Docx4JException("Failed to save package", e);
			}
	    }

	    log.info("...Done!" );		

		 return true;
	}
	
	public String encodeSlashes(NodeMapper nodeMapper,  String partName) {		
		
		if (nodeMapper instanceof org.docx4j.JcrNodeMapper.AlfrescoJcrNodeMapper) {
			
//			log.info("incoming path:" + partName);
						
			// Add CM_NAMESPACE prefix
			
			// Split it into segments, and add CM_NAMESPACE prefix
			StringBuffer retVal = new StringBuffer();
			String[] partNameSegments = partName.split("/");

			for (short i = 0 ; i < partNameSegments.length; ++i) {
				if (i>0) retVal.append("/");
				retVal.append(CM_NAMESPACE + org.docx4j.JcrNodeMapper.ISO9075.encode(partNameSegments[i]) );
			}
//			log.info("created path:" + retVal.toString());
			return retVal.toString();
		} else {
			log.debug( "Incoming: " + partName);
			log.debug( "Encoded as: " + java.net.URLEncoder.encode( partName ));
			return java.net.URLEncoder.encode( partName );
		}
		
	}


	public Node saveRawXmlPart(Node baseNode, Part part) throws Docx4JException {
		
		// This is a neater signature and should be used where possible!
		
		String partName = part.getPartName().getName().substring(1);
		
		org.w3c.dom.Document w3cDoc = null;
		
		if (part instanceof org.docx4j.openpackaging.parts.JaxbXmlPart) {

			try {
				javax.xml.parsers.DocumentBuilderFactory dbf = javax.xml.parsers.DocumentBuilderFactory.newInstance();
				dbf.setNamespaceAware(true);
				w3cDoc = dbf.newDocumentBuilder().newDocument();
				
				((org.docx4j.openpackaging.parts.JaxbXmlPart)part).marshal( w3cDoc );
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} 
			
		} else if (part instanceof org.docx4j.openpackaging.parts.Dom4jXmlPart) {

			w3cDoc = ((org.docx4j.openpackaging.parts.Dom4jXmlPart)part).getW3cDocument();
						
		} else {
			// Shouldn't happen, since ContentTypeManagerImpl should
			// return an instance of one of the above, or throw an
			// Exception.
			
			log.error("No suitable part found for: " + partName);
			return null;					
		}		
		
		return saveRawXmlPart(baseNode, partName, w3cDoc);  
		
		// TODO - refactor, so that in JaxbXmlPart case, we write
		// directly to output stream (like we do in SaveToZipFile)
		
	}
	
	/* @displayName - a human readable description for this content
	 * object.  Pass null if you don't want the displayName property set. 
	 * Returns the resource.
	 */
	public Node saveRawXmlPart(Node baseNode, String partName, org.w3c.dom.Document w3cDoc) throws Docx4JException {

		try {
			// OLD COMMENT - IN COURSE OF BECOMING REDUNDANT (as of 18 Jan 2008)  
            // NB: Nodetypes used are required for interoperability with 
            // org.apache.jackrabbit.server.   Three things are required 
			// for interoperability:
			// 1. Same node types and hierarchy (dump the structure
			//    of a document PUT via WebDAV to see what is required)
			// 2. Same handling of directories (ie ignore)
			// 3. Same encoding of file names (which is the same as
			//    2, since we encode the '/' which would otherwise
			//    be a directory.
		
			// Create an nt:file node to represent each file name
			// Encode '/' so it is a flat structure

			Node cmContentNode;			
			try {
				// Does it exist already?
				cmContentNode = baseNode.getNode(encodeSlashes(nodeMapper, partName));
				log.info(encodeSlashes(nodeMapper, partName) + " found .. ");				
			} catch (PathNotFoundException pnf) {
				log.info(encodeSlashes(nodeMapper, partName) + " not found .. so adding");
//		        cmContentNode = baseNode.addNode( encodeSlashes(nodeMapper, partName), "nt:file" );
				cmContentNode = nodeMapper.addFileNode(baseNode, encodeSlashes(nodeMapper, partName));
				
		    }	        
			
			// New
			if (cmContentNode.isNodeType("mix:versionable")) {
				log.info(" it is mix:versionable .. so checkout");					
				// check it out
				cmContentNode.checkout();
			} else {
				// Alfresco
				if (nodeMapper instanceof org.docx4j.JcrNodeMapper.AlfrescoJcrNodeMapper) {
					// An Alfresco aspect is a mixin?  Seems to be.
					// This does prevent the error "Node workspace://SpacesStore/... is not versionable"
					cmContentNode.addMixin("cm:versionable");
				}	        
				cmContentNode.addMixin("mix:versionable");	
				log.info(" made mix:versionable ");	

				// New 2008 01 18
				cmContentNode.checkout();
				
			}
			
/* With impending new content model for Jackrabbit, this is not required.
 * 
			// Then a jcr:content node for the content
	        // The jcr:content child node can be of any node type to allow maximum flexibility, 
	        // but the nt:resource node type is the common choice for jcr:content nodes.
			// (use nt:resource for binary content).
			Node contentNode; 
			try {
				// Does it exist already?
//				contentNode = fileNode.getNode("jcr:content");
				contentNode = nodeMapper.getContentNode(cmContentNode);
				
				log.info(" jcr:content found ");
				
				if (contentNode.isNodeType("mix:versionable")) {
					log.info(" it is mix:versionable .. so checkout");					
					// check it out
					contentNode.checkout();
				} else {
			        contentNode.addMixin("mix:versionable");	
			        
			        // TODO: Alfreso needs cm:versionable
			        // http://issues.alfresco.com/browse/AR-659
			        // How to add this?
			        // .. Patch Alfresco to do it?
			        
					log.info(" made mix:versionable ");					
				}
				
			} catch (PathNotFoundException pnf) {
				log.info("jcr:content not found .. so adding");
				contentNode = cmContentNode.addNode("jcr:content", "nt:unstructured");
		        contentNode.addMixin("mix:versionable");
		    }	        	        
 * 
 */ 			

	        
	        // Need to convert the Document to an input stream
	        // There are at least three possible ways to do it:
	        // 1. PipedInputStream http://www.biglist.com/lists/xsl-list/archives/199908/msg00554.html
	        // 2. Use an intermediate byte array
	        // 3. Use XSLT! http://lists.xml.org/archives/xml-dev/200408/msg00072.html
	        // In fact I use the library
	        // See http://www.ibm.com/developerworks/java/library/j-io1/
	        // Though the byte array would work ok as well.

//	        // For now, convert dom4j node to real W3C dom node
//	        // (TODO write org.merlin.io.DOM4JSerializerEngine!)
//	        org.w3c.dom.Document w3cDoc 
//	        	= new org.dom4j.io.DOMWriter().write(xml);
	        
	        DOMSerializerEngine engine 
	        	= new DOMSerializerEngine( (org.w3c.dom.Node)w3cDoc.getDocumentElement() );

	        // New - in what follows, replaced contentNode with cmContentNode
	        
	        // javax.jcr.nodetype.ConstraintViolationException: no matching property definition found for {http://www.jcp.org/jcr/1.0}data
	        cmContentNode.setProperty("jcr:mimeType", "text/plain");
//	        contentNode.setProperty("jcr:encoding", "");
	        
	        if (partName.indexOf("/")>0 ) {
	        	cmContentNode.setProperty("cm:name", partName.substring(partName.lastIndexOf("/")+1));
	        	/* Slashes in cm:name upset PropertiesIntegrityEvent.  
	        	 * 
	        	 * org.alfresco.repo.node.integrity.IntegrityException: Found 1 integrity violations:
	        		Invalid property value: 
	        		   Node: workspace://SpacesStore/73b7fe1a-c5c1-11dc-8423-e977704507ee
	        		   Type: {http://www.alfresco.org/model/content/1.0}content
	        		   Property: {http://www.alfresco.org/model/content/1.0}name
	        		   Constraint: Value 'word/_rels/document.xml.rels' matches regular expression: (.*[\"\*\\\>\<\?\/\:\|]+.*)|(.*[\.]?.*[\.]+$)|(.*[ ]+$)		        		   
	        	 */
	        	log.info("Truncated cm:name from " + partName + " to " + partName.substring(partName.lastIndexOf("/")+1) );
	        	// eg Truncated cm:name from word/_rels/document.xml.rels to document.xml.rels

	        } else {
	        	cmContentNode.setProperty("cm:name", partName);
	        }
	        cmContentNode.setProperty("cm:title", partName);
	        log.info("Set cm:name and cm:title: " + partName);
	        
	        
	        
	        // Maybe this will just work in Alfreso?
	        // If not, use setJcrDataProperty
//	        cmContentNode.setProperty("jcr:data", new OutputEngineInputStream(engine) );
	        
	        log.info("using " + nodeMapper.getClass().getName());
	        
	        nodeMapper.setJcrDataProperty(cmContentNode, new OutputEngineInputStream(engine) );	        
	        
	        Calendar lastModified = Calendar.getInstance();
	        lastModified.setTimeInMillis(lastModified.getTimeInMillis());
	        cmContentNode.setProperty("jcr:lastModified", lastModified);	
			jcrSession.save();
			
			Version firstVersion = cmContentNode.checkin();

			log.info( "PUT SUCCESS: " + partName +  "(" + cmContentNode.getPath() + ") ");		
			
			return cmContentNode;
			
		} catch (Exception e ) {
			e.printStackTrace();
			throw new Docx4JException("Failed to put " + partName, e);
		}
				
	}
	
	/* recursively 
		(i) get each Part listed in the relationships
		(ii) add the Part to the zip file
		(iii) traverse its relationship
	*/
	public void addPartsFromRelationships( 
			Node baseNode, RelationshipsPart rp )  throws Docx4JException  {
		
		for (Iterator it = rp.iterator(); it.hasNext(); ) {
			Relationship r = (Relationship)it.next();
			log.info("For Relationship Id=" + r.getId() 
					+ " Source is " + r.getSource().getPartName() 
					+ ", Target is " + r.getTargetURI() );
			
			if (!r.getTargetMode().equals(TargetMode.INTERNAL) ) {
				
				// ie its EXTERNAL
				// As at 1 May 2008, we don't have a Part for these;
				// there is just the relationship.

				log.warn("Encountered external resource " + r.getTargetURI() 
						   + " of type " + r.getRelationshipType() );
				
				// So
				continue;				
			}
			
			try {
				String resolvedPartUri = URIHelper.resolvePartUri(r.getSourceURI(), r.getTargetURI() ).toString();
				// Now drop leading "/'
				resolvedPartUri = resolvedPartUri.substring(1);				
				
				// Now normalise it .. ie abc/def/../ghi
				// becomes abc/ghi
				// to deal with word/../customXml/item1.xml
				// in my UNHCR Arabic doc
//				target = (new java.net.URI(target)).normalize().toString();
//				log.info("Normalised, it is " + target );
				
				
				// TODO - if this is already in our hashmap, skip
				// to the next				
				if (!false) {
					log.info("Getting part /" + resolvedPartUri );
					Part part = p.getParts().get(new PartName("/" + resolvedPartUri));
					log.info( part.getClass());
					
					savePart(baseNode, part);
					
				}

			} catch (Exception e) {
				throw new Docx4JException("Failed to add parts from relationships", e);
			}
		}
		
		
	}

	/**
	 * Save a Part (not a relationship part though) to JCR, along with its relationship
	 * part (if any), and any parts referred to in its relationships, 
	 * and so on, recursively.
	 * @param baseNode
	 * @param target
	 * @param part
	 * @throws Docx4JException
	 */
	public void savePart(Node baseNode, Part part)
			throws Docx4JException {
		
		// Drop the leading '/'
		String resolvedPartUri = part.getPartName().getName().substring(1);
		if (part instanceof BinaryPart) {
			log.info(".. saving binary stuff" );
			saveRawBinaryPart( baseNode, part );
			
		} else {
			log.info(".. saving " );
			saveRawXmlPart( baseNode, part );
		}
		
		// recurse via this parts relationships, if it has any
		if (part.getRelationshipsPart()!= null ) {
			RelationshipsPart rrp = part.getRelationshipsPart(); // **
			String relPart = PartName.getRelationshipsPartName(resolvedPartUri);
			log.info("Found relationships " + relPart );
			saveRawXmlPart(baseNode,  relPart, rrp.getW3cDocument() );
			log.info("Recursing ... " );
			addPartsFromRelationships( baseNode, rrp );
		} else {
			log.info("No relationships for " + resolvedPartUri );					
		}
	}

	/* put binary parts */
	protected void saveRawBinaryPart(Node baseNode, Part part) throws Docx4JException {

		// Drop the leading '/'
		String resolvedPartUri = part.getPartName().getName().substring(1);

		InputStream bin = org.docx4j.utils.BufferUtil.newInputStream(
							((BinaryPart)part).getBuffer() );
		
		try {
		
			// Create an nt:file node to represent each file name
			// Encode '/' so it is a flat structure
			Node cmContentNode;			
			try {
				// Does it exist already?
				cmContentNode = baseNode.getNode(encodeSlashes(nodeMapper, resolvedPartUri));
				log.info(encodeSlashes(nodeMapper, resolvedPartUri) + " found .. ");				
			} catch (PathNotFoundException pnf) {
				log.info(encodeSlashes(nodeMapper, resolvedPartUri) + " not found .. so adding");
//		        cmContentNode = baseNode.addNode( encodeSlashes(nodeMapper, resolvedPartUri), "nt:file" );
				cmContentNode = nodeMapper.addFileNode(baseNode, encodeSlashes(nodeMapper, resolvedPartUri));				
		    }
			
			// New
			if (cmContentNode.isNodeType("mix:versionable")) {
				log.info(" it is mix:versionable .. so checkout");					
				// check it out
				cmContentNode.checkout();
			} else {
				// Alfresco
				if (nodeMapper instanceof org.docx4j.JcrNodeMapper.AlfrescoJcrNodeMapper) {
					// An Alfresco aspect is a mixin?  Seems to be.
					// This does prevent the error "Node workspace://SpacesStore/... is not versionable"
					cmContentNode.addMixin("cm:versionable");
				}	        
				cmContentNode.addMixin("mix:versionable");	
				log.info(" made mix:versionable ");	

				// New 2008 01 18
				cmContentNode.checkout();
			}
			
			
/* With impending new content model for Jackrabbit, this is not required.
 * 

			Node contentNode; 
			try {
				// Does it exist already?
				contentNode = cmContentNode.getNode("jcr:content");
				log.info(" jcr:content found ");
				if (contentNode.isNodeType("mix:versionable")) {
					log.info(" it is mix:versionable.. so checkout");					
					// check it out
					contentNode.checkout();
				} else {
			        contentNode.addMixin("mix:versionable");					
					log.info(" made mix:versionable");					
				}
			} catch (PathNotFoundException pnf) {
				log.info("jcr:content not found .. so adding");
				contentNode = cmContentNode.addNode("jcr:content", "nt:unstructured");
		        contentNode.addMixin("mix:versionable");
		    }	        	        
*/			
	        // New - in what follows, replaced contentNode with cmContentNode
	        
			cmContentNode.setProperty("jcr:mimeType", "application/octet-stream");
//	        contentNode.setProperty("jcr:encoding", "");
	        
			//cmContentNode.setProperty("jcr:data", bin );
	        nodeMapper.setJcrDataProperty(cmContentNode, bin );	        
			
	        Calendar lastModified = Calendar.getInstance();
	        lastModified.setTimeInMillis(lastModified.getTimeInMillis());
	        cmContentNode.setProperty("jcr:lastModified", lastModified);	
			jcrSession.save();
			
			Version firstVersion = cmContentNode.checkin();
		} catch (Exception e ) {
			throw new Docx4JException("Failed to put binary part", e);			
		}
	}
	
	
	
    private  String dump(Node node, String indent) throws RepositoryException {
        StringBuilder sb=new StringBuilder();
        String sep=",";
        sb.append(indent + node.getName());
        sb.append("["+node.getPath());
        PropertyIterator propIterator=node.getProperties();
        while(propIterator.hasNext()) {
            Property prop=propIterator.nextProperty();
            sb.append(sep);
            sb.append("\n" + indent + "@"+prop.getName()+"=\""+prop.getString()+"\"");
        }
        sb.append("]");
        
        NodeIterator nodeIterator = node.getNodes();
        while(nodeIterator.hasNext()) {
            Node n=nodeIterator.nextNode();
            sb.append( "\n\n" + dump( n, indent + "    " ));
            
        }
        
        
        return sb.toString();
    }
    
  
 	
	protected void debugPrint( Document coreDoc) {
		try {
			OutputFormat format = OutputFormat.createPrettyPrint();
		    XMLWriter writer = new XMLWriter( System.out, format );
		    writer.write( coreDoc );
		} catch (Exception e ) {
			e.printStackTrace();
		}	    
	}
		
//	// Testing - Jackrabbit only
//	public static void main(String[] args) throws Exception {
//		Session jcrSession = null;
//        try {
//
//        	//We need:
//        	
//        	// 1. a Session
//	        Repository repository = new TransientRepository();
//	        jcrSession = repository.login(
//	                new SimpleCredentials("username", "password".toCharArray()));
//	        
//	        // 2. a Package
//			WordprocessingMLPackage p = WordprocessingMLPackage.createTestPackage();
//    		log.info( "Test package created..");
//
//    		// 3. a Node to save to
//    		Node root = jcrSession.getRootNode();
//            Node customers = root.addNode("customers", "nt:folder");
//            Node customer = customers.addNode("contoso", "nt:folder");
//            Node docs = customer.addNode("docs", "nt:folder");
//            Node exampledoc1 = docs.addNode("exampledoc1.docx", "nt:file");            
//            // This node required for interoperability with 
//            // org.apache.jackrabbit.server
//            	// TODO 20080118 - review in light of new content model
//	        Node contentNode = exampledoc1.addNode("jcr:content", "nt:unstructured");
//	        
//	        // Now we can do the actual save.
//    		org.docx4j.JcrNodeMapper.JackrabbitJcrNodeMapper nodeMapper = new org.docx4j.JcrNodeMapper.JackrabbitJcrNodeMapper(); 	        
//	        SaveToJCR saver = new SaveToJCR(p, nodeMapper, jcrSession);
//	        saver.save(contentNode );		
//	        
//        } catch(Exception e) {
//            e.printStackTrace();	        
//        } finally {
//        	jcrSession.logout();
//        }
//	}

	
	
}