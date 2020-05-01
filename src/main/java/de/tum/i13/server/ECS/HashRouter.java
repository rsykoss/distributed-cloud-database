package de.tum.i13.server.ECS;

import de.tum.i13.shared.Node;
import de.tum.i13.shared.HashFunction;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.logging.Logger;
import java.util.Iterator;

public class HashRouter<T extends Node> {
    public final ConcurrentSkipListMap<String, VirtualNode<T>> ring = new ConcurrentSkipListMap<>();
    private final HashFunction hashFunction;
    public static Logger logger = Logger.getLogger(HashRouter.class.getName());

    public HashRouter() {
        this(new MD5Hash());
    }

    public HashRouter(HashFunction hashFunction) {
        if (hashFunction == null)
            throw new NullPointerException("Hash Function is null");
        logger.info("Operating hash router");
        this.hashFunction = hashFunction;
        // if (pNodes != null){
        // for (T pNode : pNodes){
        // addNode(pNode, vNodeCount);
        // }
        // }
    }

    public void addNode(T pNode, int vNodeCount) {
        if (vNodeCount < 0)
            throw new IllegalArgumentException("Illegal virtual node counts:" + vNodeCount);
        int existingReplicas = getExistingReplicas(pNode);
        for (int i = 0; i < vNodeCount; i++) {
            logger.info("Adding node (server)");
            VirtualNode<T> vNode = new VirtualNode<>(pNode, i + existingReplicas);
            ring.put(hashFunction.hash(vNode.getKey()), vNode);
        }
    }

    public void removeNode(T pNode) {
        Iterator<String> it = ring.keySet().iterator();
        while (it.hasNext()) {
            String key = it.next();
            VirtualNode<T> virtualNode = ring.get(key);
            if (virtualNode.isVirtualNodeOf(pNode)) {
                logger.info("Removing node (server)");
                it.remove();
            }
        }
    }

    public T routeNode(String objectKey) {
        if (ring.isEmpty()) {
            return null;
        }
        String hashVal = getHash(objectKey);
        ConcurrentNavigableMap<String, VirtualNode<T>> tailMap = ring.tailMap(hashVal);
        String nodeHashVal = !tailMap.isEmpty() ? tailMap.firstKey() : ring.firstKey();
        return ring.get(nodeHashVal).getPhysicalNode();
    }

    public int getExistingReplicas(T pNode) {
        int replicas = 0;
        for (VirtualNode<T> vNode : ring.values()) {
            if (vNode.isVirtualNodeOf(pNode)) {
                replicas++;
            }
        }
        return replicas;
    }

    public String getHash(String objectKey) {
        String hashVal = hashFunction.hash(objectKey);
        return hashVal;
    }

    private static class MD5Hash implements HashFunction {
        MessageDigest instance;

        public MD5Hash() {
            try {
                instance = MessageDigest.getInstance("MD5");
            } catch (NoSuchAlgorithmException e) {
                logger.warning(e.toString());
                e.printStackTrace();
            }
        }

        @Override
        public String hash(String key) {
            instance.reset();
            instance.update(key.getBytes());
            byte[] digest = instance.digest();
            // long hash = UUID.nameUUIDFromBytes(key.getBytes()).getMostSignificantBits();
            // System.out.println(digest);
            StringBuffer hexString = new StringBuffer();
            for (int i = 0; i < digest.length; i++) {
                String hex = Integer.toHexString(0xFF & digest[i]);
                if (hex.length() == 1)
                    hexString.append('0');

                hexString.append(hex);
            }
            // long h = 0;
            // for (int i = 0; i < 4; i++) {
            // h <<= 8;
            // h |= ((int) digest[i]) & 0xFF;
            // }
            return hexString.toString();
        }
    }

}
