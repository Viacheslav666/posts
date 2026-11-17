package ru.posts.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "channel_set_items")
@IdClass(ChannelSetItemEntity.ChannelSetItemId.class)
public class ChannelSetItemEntity {

    @Id
    @Column(name = "set_id", nullable = false)
    private UUID setId;

    @Id
    @Column(name = "channel_id", nullable = false)
    private UUID channelId;

    public UUID getSetId() {
        return setId;
    }

    public void setSetId(UUID setId) {
        this.setId = setId;
    }

    public UUID getChannelId() {
        return channelId;
    }

    public void setChannelId(UUID channelId) {
        this.channelId = channelId;
    }

    public static final class ChannelSetItemId implements Serializable {
        private UUID setId;
        private UUID channelId;

        public ChannelSetItemId() {}

        public ChannelSetItemId(UUID setId, UUID channelId) {
            this.setId = setId;
            this.channelId = channelId;
        }

        public UUID getSetId() {
            return setId;
        }

        public void setSetId(UUID setId) {
            this.setId = setId;
        }

        public UUID getChannelId() {
            return channelId;
        }

        public void setChannelId(UUID channelId) {
            this.channelId = channelId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ChannelSetItemId that)) {
                return false;
            }
            return Objects.equals(setId, that.setId) && Objects.equals(channelId, that.channelId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(setId, channelId);
        }
    }
}
