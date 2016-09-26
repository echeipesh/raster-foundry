"""Python class representation of a Raster Foundry thumbnail"""

from .base import BaseModel

class Thumbnail(BaseModel):

    URL_PATH = '/api/thumbnails/'

    def __init__(self, organizationId, widthPx, heightPx, size, url, id=None, sceneId=None,
                 createdAt=None, modifiedAt=None):
        """Creates a new Thumbnail

        Args:
            orgnizationId (str): UUID of organization that this scene belongs to
            widthPx (int): width of thumbnail
            heightPx (int): height of thumbnail
            size (str): size of image (small, large, square)
            url (str): location of thumbnail
            id (str): UUID of thumbnail
            scene (str): UUID of scene associated with thumbnail
        """
        self.organizationId = organizationId
        self.widthPx = widthPx
        self.heightPx = heightPx
        self.size = size
        self.url = url

        self.id = id
        self.sceneId = sceneId
        self.createdAt = createdAt
        self.modifiedAt = modifiedAt

    def __repr__(self):
        return '<Thumbnail: size-{} loc-{}>'.format(self.size, self.url)

    @classmethod
    def from_dict(cls, d):
        return cls(
            d.get('organizationId'), d.get('widthPx'), d.get('heightPx'), d.get('size'), d.get('url'),
            d.get('id'), d.get('sceneId'), d.get('createdAt'), d.get('modifiedAt')
        )

    def to_dict(self):
        thumbnail_dict = dict(
            organizationId=self.organizationId,
            widthPx=self.widthPx,
            heightPx=self.heightPx,
            size=self.size,
            url=self.url
        )

        if self.id:
            thumbnail_dict['id'] = self.id
        if self.sceneId:
            thumbnail_dict['sceneId'] = self.sceneId
        if self.createdAt:
            thumbnail_dict['createdAt'] = self.createdAt
        if self.modifiedAt:
            thumbnail_dict['modifiedAt'] = self.modifiedAt
        return thumbnail_dict

    def create(self):
        assert self.sceneId, 'Scene ID is required to create a Thumbnail'
        return super(Thumbnail, self).create()