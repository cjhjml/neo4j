package org.neo4j.impl.nioneo.xa;

import java.io.IOException;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import org.neo4j.impl.core.PropertyIndex;
import org.neo4j.impl.core.RawPropertyIndex;
import org.neo4j.impl.nioneo.store.IdGenerator;
import org.neo4j.impl.nioneo.store.NeoStore;
import org.neo4j.impl.nioneo.store.NodeStore;
import org.neo4j.impl.nioneo.store.PropertyData;
import org.neo4j.impl.nioneo.store.PropertyStore;
import org.neo4j.impl.nioneo.store.RelationshipData;
import org.neo4j.impl.nioneo.store.RelationshipStore;
import org.neo4j.impl.nioneo.store.RelationshipTypeData;
import org.neo4j.impl.nioneo.store.RelationshipTypeStore;
import org.neo4j.impl.transaction.xaframework.XaConnection;
import org.neo4j.impl.transaction.xaframework.XaConnectionHelpImpl;
import org.neo4j.impl.transaction.xaframework.XaResourceHelpImpl;
import org.neo4j.impl.transaction.xaframework.XaResourceManager;

/**
 * {@link XaConnection} implementation for the NioNeo data store. Contains
 * getter methods for the different stores (node,relationship,property and
 * relationship type).
 * <p>
 * A <CODE>NeoStoreXaConnection</CODE> is obtained from 
 * {@link NeoStoreXaDataSource} and then Neo persistence layer can perform 
 * the operations requested via the store implementations.  
 */
public class NeoStoreXaConnection extends XaConnectionHelpImpl
{
	
	private NeoStoreXaResource xaResource = null;
	 
	private final NeoStore neoStore;
	private final NodeEventConsumer nodeConsumer;
	private final RelationshipEventConsumer relConsumer;
	private final RelationshipTypeEventConsumer relTypeConsumer;
	private final PropertyIndexEventConsumer propIndexConsumer;
	
	private NeoTransaction neoTransaction = null;
	
	NeoStoreXaConnection( NeoStore neoStore, XaResourceManager xaRm )
	{
		super( xaRm );
		this.neoStore = neoStore;
		
		this.nodeConsumer = new NodeEventConsumerImpl( this ); 
		this.relConsumer = new RelationshipEventConsumerImpl( this ); 
		this.relTypeConsumer = new RelationshipTypeEventConsumerImpl( this );
		this.propIndexConsumer = new PropertyIndexEventConsumerImpl( this );
		this.xaResource = new NeoStoreXaResource( xaRm );
	}

	/**
	 * Returns this neo store's {@link NodeStore}.
	 * 
	 * @return The node store
	 */
	public NodeEventConsumer getNodeConsumer()
	{
		return nodeConsumer;
	}
	
	/**
	 * Returns this neo store's {@link RelationshipStore}.
	 * 
	 * @return The relationship store
	 */
	public RelationshipEventConsumer getRelationshipConsumer()
	{
		return relConsumer;
	}
	
	public PropertyIndexEventConsumer getPropertyIndexConsumer()
	{
		return propIndexConsumer;
	}
	
	/**
	 * Returns this neo store's {@link RelationshipTypeStore}.
	 * 
	 * @return The relationship type store
	 */
	public RelationshipTypeEventConsumer getRelationshipTypeConsumer()
	{
		return relTypeConsumer;
	}
	/**
	 * Made public for testing, dont use.
	 */
	public PropertyStore getPropertyStore()
	{
		return neoStore.getPropertyStore();
	}
	
	NodeStore getNodeStore()
	{
		return neoStore.getNodeStore();
	}

	RelationshipStore getRelationshipStore()
	{
		return neoStore.getRelationshipStore();
	}
	
	RelationshipTypeStore getRelationshipTypeStore()
	{
		return neoStore.getRelationshipTypeStore();
	}
	
	public XAResource getXaResource()
	{
		return this.xaResource;
	}
	
	NeoTransaction getNeoTransaction() throws IOException
	{
		if ( neoTransaction != null )
		{
			return neoTransaction;
		}
		try
		{
			neoTransaction = ( NeoTransaction ) getTransaction();
			return neoTransaction;
		}
		catch ( XAException e )
		{
			throw new IOException( "Unable to get transaction, " + e );
		}
	}
	
	private static class NeoStoreXaResource extends XaResourceHelpImpl
	{
		NeoStoreXaResource( XaResourceManager xaRm )
		{
			super( xaRm );
		}
		
		public boolean isSameRM( XAResource xares )
		{
			if ( xares instanceof NeoStoreXaResource )
			{
				// check for same store here later?
				return true;
			}
			return false;
		}		
	};
	
	private class NodeEventConsumerImpl implements NodeEventConsumer
	{
		private final NeoStoreXaConnection xaCon;
		private final NodeStore nodeStore;
		
		public NodeEventConsumerImpl( NeoStoreXaConnection xaCon )
		{
			this.xaCon = xaCon;
			nodeStore = getNodeStore();
		}
		
		public void createNode( int nodeId ) throws IOException
		{
			xaCon.getNeoTransaction().nodeCreate( nodeId );
		}
		
		public void deleteNode( int nodeId ) throws IOException
		{
			xaCon.getNeoTransaction().nodeDelete( nodeId );
		}
		
		// checks for created in tx else get from store
		public boolean loadLightNode( int nodeId ) throws IOException 
		{
			return xaCon.getNeoTransaction().nodeLoadLight( nodeId );
		}
		
		public void addProperty( int nodeId, int propertyId, 
			PropertyIndex index, Object value ) throws IOException
		{
			xaCon.getNeoTransaction().nodeAddProperty( nodeId, propertyId, 
				index, value ); 
		}
	
		public void changeProperty( int nodeId, int propertyId, Object value ) 
			throws IOException
		{
			xaCon.getNeoTransaction().nodeChangeProperty( nodeId, propertyId, 
				value );
		}
		
		public void removeProperty( int nodeId, int propertyId ) 
			throws IOException
		{
			xaCon.getNeoTransaction().nodeRemoveProperty( nodeId, propertyId ); 
		}
		
		public PropertyData[] getProperties( int nodeId ) throws IOException
		{
			return xaCon.getNeoTransaction().nodeGetProperties( nodeId );			
		}
		
		public RelationshipData[] getRelationships( int nodeId ) 
			throws IOException
		{
			return xaCon.getNeoTransaction().nodeGetRelationships( nodeId );
		}
		
		public int nextId() throws IOException
		{
			return nodeStore.nextId();
		}
		
		public IdGenerator getIdGenerator()
		{
			throw new RuntimeException( "don't use" );
		}
	};		
	
	private class RelationshipEventConsumerImpl implements 
		RelationshipEventConsumer
	{
		private final NeoStoreXaConnection xaCon;
		private final RelationshipStore relStore;
		
		public RelationshipEventConsumerImpl( NeoStoreXaConnection xaCon )
		{
			this.xaCon = xaCon;
			this.relStore = getRelationshipStore();
		}
		
		public void createRelationship( int id, int firstNode, int secondNode, 
			int type ) throws IOException
		{
			xaCon.getNeoTransaction().relationshipCreate( id, firstNode, 
				secondNode, type );		
		}
		
		public void deleteRelationship( int id ) throws IOException
		{
			xaCon.getNeoTransaction().relDelete( id );		
		}
	
		public void addProperty( int relId, int propertyId, PropertyIndex index, 
			Object value ) throws IOException
		{
			xaCon.getNeoTransaction().relAddProperty( relId, propertyId, index, 
				value );		
		}
	
		public void changeProperty( int relId, int propertyId, Object value ) 
			throws IOException
		{
			xaCon.getNeoTransaction().relChangeProperty( relId, propertyId, 
				value );		
		}
		
		public void removeProperty( int relId, int propertyId ) 
			throws IOException
		{
			xaCon.getNeoTransaction().relRemoveProperty( relId, propertyId );		
		}
		
		public PropertyData[] getProperties( int relId )
			throws IOException
		{
			return xaCon.getNeoTransaction().relGetProperties( relId );
		}
	
		public RelationshipData getRelationship( int id ) throws IOException
		{
			return xaCon.getNeoTransaction().relationshipLoad( id );
		}
		
		public int nextId() throws IOException
		{
			return relStore.nextId();
		}

		public IdGenerator getIdGenerator()
		{
			throw new RuntimeException( "don't use" );
		}
	};

	private class RelationshipTypeEventConsumerImpl 
		implements RelationshipTypeEventConsumer
	{
		private final NeoStoreXaConnection xaCon;
		private final RelationshipTypeStore relTypeStore;
		
		RelationshipTypeEventConsumerImpl( NeoStoreXaConnection xaCon )
		{
			this.xaCon = xaCon;
			this.relTypeStore = getRelationshipTypeStore();
		}
		
		public void addRelationshipType( int id, String name ) 
			throws IOException
		{
			xaCon.getNeoTransaction().relationshipTypeAdd( id, name );		
		}

		public RelationshipTypeData getRelationshipType( int id ) 
			throws IOException
		{
			return relTypeStore.getRelationshipType( id );
		}
	
		public RelationshipTypeData[] getRelationshipTypes()
			throws IOException
		{
			return relTypeStore.getRelationshipTypes();
		}
		
		public int nextId() throws IOException
		{
			return relTypeStore.nextId();
		}
	};

	private class PropertyIndexEventConsumerImpl 
		implements PropertyIndexEventConsumer
	{
		private final NeoStoreXaConnection xaCon;
		
		PropertyIndexEventConsumerImpl( NeoStoreXaConnection xaCon )
		{
			this.xaCon = xaCon;
		}
		
		public void createPropertyIndex( int id, String key ) 
			throws IOException
		{
			xaCon.getNeoTransaction().createPropertyIndex( id, key );		
		}

		public String getKeyFor( int id ) throws IOException
		{
			return xaCon.getNeoTransaction().getPropertyIndex( id );
		}
	
		public RawPropertyIndex[] getPropertyIndexes( int count ) 
			throws IOException
		{
			return xaCon.getNeoTransaction().getPropertyIndexes( count );
		}
	};
}
